package com.mycompany.gymbooking.service;

import com.mycompany.gymbooking.dto.AdminTrainerResponse;
import com.mycompany.gymbooking.dto.ScheduleRequest;
import com.mycompany.gymbooking.dto.TrainerDeactivationResponse;
import com.mycompany.gymbooking.dto.TrainerRequest;
import com.mycompany.gymbooking.dto.WorkingHoursRequest;
import com.mycompany.gymbooking.exception.BadRequestException;
import com.mycompany.gymbooking.exception.ConflictException;
import com.mycompany.gymbooking.exception.NotFoundException;
import com.mycompany.gymbooking.model.Booking;
import com.mycompany.gymbooking.model.Branch;
import com.mycompany.gymbooking.model.Trainer;
import com.mycompany.gymbooking.model.TrainerStatus;
import com.mycompany.gymbooking.model.WorkingHours;
import com.mycompany.gymbooking.notification.NotificationSender;
import com.mycompany.gymbooking.repository.BookingRepository;
import com.mycompany.gymbooking.repository.BookingRepository.TrainerBookingCount;
import com.mycompany.gymbooking.repository.BranchRepository;
import com.mycompany.gymbooking.repository.TrainerRepository;
import com.mycompany.gymbooking.repository.UserRepository;
import com.mycompany.gymbooking.repository.WorkingHoursRepository;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminTrainerServiceImpl implements AdminTrainerService {

    private static final DateTimeFormatter EXPIRY = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.ENGLISH);
    private static final String DEFAULT_DEACTIVATION_NOTE = "Your trainer is no longer available.";

    private final TrainerRepository trainerRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final PasswordEncoder passwordEncoder;
    private final NotificationSender notificationSender;
    private final VerificationCodeGenerator codeGenerator;
    private final Clock clock;
    private final long inviteValidityDays;

    public AdminTrainerServiceImpl(TrainerRepository trainerRepository,
                                   UserRepository userRepository,
                                   BranchRepository branchRepository,
                                   WorkingHoursRepository workingHoursRepository,
                                   BookingRepository bookingRepository,
                                   BookingService bookingService,
                                   PasswordEncoder passwordEncoder,
                                   NotificationSender notificationSender,
                                   VerificationCodeGenerator codeGenerator,
                                   Clock clock,
                                   @Value("${app.trainer-invite.expiration-days}") long inviteValidityDays) {
        this.trainerRepository = trainerRepository;
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.workingHoursRepository = workingHoursRepository;
        this.bookingRepository = bookingRepository;
        this.bookingService = bookingService;
        this.passwordEncoder = passwordEncoder;
        this.notificationSender = notificationSender;
        this.codeGenerator = codeGenerator;
        this.clock = clock;
        this.inviteValidityDays = inviteValidityDays;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminTrainerResponse> list() {
        List<Trainer> trainers = trainerRepository.findAllByOrderByFullNameAsc();
        List<Long> ids = trainers.stream().map(Trainer::getId).toList();
        Map<Long, List<WorkingHours>> hoursByTrainer = workingHoursRepository.findByTrainerIdIn(ids).stream()
                .collect(Collectors.groupingBy(hours -> hours.getTrainer().getId()));
        Map<Long, Long> upcoming = upcomingBookingCounts();

        return trainers.stream()
                .map(trainer -> AdminTrainerResponse.from(trainer,
                        hoursByTrainer.getOrDefault(trainer.getId(), List.of()),
                        upcoming.getOrDefault(trainer.getId(), 0L)))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminTrainerResponse get(Long trainerId) {
        return response(findTrainer(trainerId));
    }

    @Override
    @Transactional
    public AdminTrainerResponse create(TrainerRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
        }
        Branch branch = findBranch(request.branchId());

        // A random password nobody knows; the trainer sets their own when accepting the invite.
        Trainer trainer = new Trainer(request.fullName().trim(), email, request.phone().trim(),
                passwordEncoder.encode(UUID.randomUUID().toString()),
                request.specialty().trim(), clean(request.bio()), request.yearsOfExperience());
        applyDetails(trainer, request, branch);
        String code = issueInviteCode(trainer);
        trainerRepository.saveAndFlush(trainer);   // surface constraint errors before the email goes out

        sendInvite(trainer, code);
        return response(trainer);
    }

    @Override
    @Transactional
    public AdminTrainerResponse update(Long trainerId, TrainerRequest request) {
        Trainer trainer = findTrainer(trainerId);
        Branch branch = findBranch(request.branchId());

        String email = normalizeEmail(request.email());
        boolean emailChanged = !email.equalsIgnoreCase(trainer.getEmail());
        if (emailChanged) {
            if (userRepository.existsByEmailIgnoreCase(email)) {
                throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
            }
            trainer.changeEmail(email);
        }
        applyDetails(trainer, request, branch);
        trainerRepository.saveAndFlush(trainer);

        if (emailChanged && trainer.getStatus() == TrainerStatus.INVITED) {
            sendInvite(trainer, issueInviteCode(trainer));
        }
        return response(trainer);
    }

    @Override
    @Transactional
    public AdminTrainerResponse updateSchedule(Long trainerId, ScheduleRequest request) {
        Trainer trainer = findTrainer(trainerId);
        validate(request.blocks());

        workingHoursRepository.deleteByTrainerId(trainerId);
        workingHoursRepository.saveAll(request.blocks().stream()
                .map(block -> new WorkingHours(trainer, block.dayOfWeek(), block.startTime(), block.endTime()))
                .toList());
        return response(trainer);
    }

    @Override
    @Transactional
    public AdminTrainerResponse resendInvite(Long trainerId) {
        Trainer trainer = findTrainer(trainerId);
        switch (trainer.getStatus()) {
            case ACTIVE -> throw new ConflictException("INVITE_ALREADY_ACCEPTED", "This trainer has already joined.");
            case DEACTIVATED -> throw new ConflictException("TRAINER_DEACTIVATED", "Reactivate this trainer first.");
            case INVITED -> sendInvite(trainer, issueInviteCode(trainer));
        }
        return response(trainer);
    }

    /** Locks the trainer so no new booking request can slip in while their bookings are cancelled. */
    @Override
    @Transactional
    public TrainerDeactivationResponse deactivate(Long trainerId, String reason) {
        Trainer trainer = trainerRepository.findLockedById(trainerId)
                .orElseThrow(() -> trainerNotFound(trainerId));
        if (!trainer.isActive()) {
            throw new ConflictException("TRAINER_ALREADY_DEACTIVATED", "This trainer is already deactivated.");
        }
        trainer.deactivate(LocalDateTime.now(clock));

        String note = reason == null || reason.isBlank() ? DEFAULT_DEACTIVATION_NOTE : reason.trim();
        GymCancellations cancellations = bookingService.cancelUpcomingForTrainer(trainerId, note);
        return new TrainerDeactivationResponse(response(trainer), cancellations.cancelled(), cancellations.refunded());
    }

    @Override
    @Transactional
    public AdminTrainerResponse reactivate(Long trainerId) {
        Trainer trainer = findTrainer(trainerId);
        if (trainer.isActive()) {
            throw new ConflictException("TRAINER_ALREADY_ACTIVE", "This trainer is already active.");
        }
        trainer.reactivate();
        return response(trainer);
    }

    private void applyDetails(Trainer trainer, TrainerRequest request, Branch branch) {
        trainer.setFullName(request.fullName().trim());
        trainer.setPhone(request.phone().trim());
        trainer.setSpecialty(request.specialty().trim());
        trainer.setBio(clean(request.bio()));
        trainer.setYearsOfExperience(request.yearsOfExperience());
        trainer.assignToBranch(branch);
        trainer.changeHourlyRate(request.hourlyRate());
        trainer.updateProfile(request.category(), request.gender(), clean(request.languages()),
                cleanList(request.tags()), cleanList(request.certifications()));
    }

    /** Rejects blocks that end before they start or overlap another block on the same day. */
    private static void validate(List<WorkingHoursRequest> blocks) {
        Map<DayOfWeek, List<WorkingHoursRequest>> byDay = blocks.stream()
                .collect(Collectors.groupingBy(WorkingHoursRequest::dayOfWeek));
        for (Map.Entry<DayOfWeek, List<WorkingHoursRequest>> day : byDay.entrySet()) {
            String dayName = day.getKey().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            List<WorkingHoursRequest> sorted = day.getValue().stream()
                    .sorted(Comparator.comparing(WorkingHoursRequest::startTime))
                    .toList();
            for (int i = 0; i < sorted.size(); i++) {
                WorkingHoursRequest block = sorted.get(i);
                if (!block.endTime().isAfter(block.startTime())) {
                    throw new BadRequestException("INVALID_SCHEDULE",
                            dayName + ": " + range(block) + " ends before it starts.");
                }
                if (i > 0 && block.startTime().isBefore(sorted.get(i - 1).endTime())) {
                    throw new BadRequestException("INVALID_SCHEDULE",
                            dayName + ": " + range(sorted.get(i - 1)) + " overlaps " + range(block) + ".");
                }
            }
        }
    }

    private static String range(WorkingHoursRequest block) {
        return block.startTime() + "-" + block.endTime();
    }

    /** Uses the verification-code fields; acceptInvite checks the code and sets the password. */
    private String issueInviteCode(Trainer trainer) {
        String code = codeGenerator.generate();
        LocalDateTime now = LocalDateTime.now(clock);
        trainer.issueVerificationCode(code, now, now.plusDays(inviteValidityDays));
        return code;
    }

    private void sendInvite(Trainer trainer, String code) {
        notificationSender.send(trainer.getEmail(), "You're invited to join Gym Booking as a trainer",
                "Hi " + trainer.getFullName().split(" ")[0] + ",\n\n"
                        + "You've been added as a trainer at " + trainer.getBranch().getName() + ".\n"
                        + "To set up your account:\n"
                        + "  1. Open the Gym Booking app.\n"
                        + "  2. On the login screen, tap \"I have an invite code\".\n"
                        + "  3. Enter this email address and the code below, then choose your password.\n\n"
                        + "Your invite code is: " + code + "\n"
                        + "It expires on " + trainer.getVerificationCodeExpiresAt().format(EXPIRY) + ".");
    }

    private AdminTrainerResponse response(Trainer trainer) {
        return AdminTrainerResponse.from(trainer,
                workingHoursRepository.findByTrainerId(trainer.getId()),
                upcomingBookingCounts().getOrDefault(trainer.getId(), 0L));
    }

    private Map<Long, Long> upcomingBookingCounts() {
        LocalDateTime now = LocalDateTime.now(clock);
        return bookingRepository.countUpcomingByTrainer(Booking.SLOT_HOLDING, now.toLocalDate(), now.toLocalTime())
                .stream()
                .collect(Collectors.toMap(TrainerBookingCount::getTrainerId, TrainerBookingCount::getBookings));
    }

    private Trainer findTrainer(Long trainerId) {
        return trainerRepository.findById(trainerId).orElseThrow(() -> trainerNotFound(trainerId));
    }

    private static NotFoundException trainerNotFound(Long trainerId) {
        return new NotFoundException("TRAINER_NOT_FOUND", "No trainer with id " + trainerId);
    }

    private Branch findBranch(Long branchId) {
        return branchRepository.findById(branchId)
                .orElseThrow(() -> new BadRequestException("BRANCH_NOT_FOUND", "No branch with id " + branchId));
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private static List<String> cleanList(List<String> items) {
        return items == null ? List.of() : items.stream().map(String::trim).filter(item -> !item.isEmpty()).toList();
    }
}
