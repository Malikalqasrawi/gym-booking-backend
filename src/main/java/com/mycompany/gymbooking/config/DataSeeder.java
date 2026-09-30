package com.mycompany.gymbooking.config;

import com.mycompany.gymbooking.config.StarterData.BranchSeed;
import com.mycompany.gymbooking.config.StarterData.TrainerSeed;
import com.mycompany.gymbooking.model.Admin;
import com.mycompany.gymbooking.model.Branch;
import com.mycompany.gymbooking.model.Trainer;
import com.mycompany.gymbooking.model.WorkingHours;
import com.mycompany.gymbooking.repository.BranchRepository;
import com.mycompany.gymbooking.repository.UserRepository;
import com.mycompany.gymbooking.repository.WorkingHoursRepository;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs once at startup and adds the starter data (StarterData) that is MISSING, so you can try the app
 * right away. It checks before adding, so restarting the backend never creates duplicates, and data you
 * already have is kept (it only fills in what's empty).
 *
 *   Branches:  5 around Amman (Abdoun, Khalda, Sweifieh, Shmeisani, Jubeiha)
 *   Admin:     admin@gym.com / Admin1234
 *   Trainers:  22, e.g. sara.trainer@gym.com, yousef.trainer@gym.com ... all with password Trainer1234
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String TRAINER_PASSWORD = "Trainer1234";

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                      BranchRepository branchRepository,
                      WorkingHoursRepository workingHoursRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.workingHoursRepository = workingHoursRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /** @Transactional: everything below is one unit, and LAZY fields can be read safely. */
    @Override
    @Transactional
    public void run(String... args) {
        seedBranches();   // first: trainers need a branch to be assigned to
        seedAdmin();

        int created = 0;
        for (int i = 0; i < StarterData.TRAINERS.size(); i++) {
            if (seedTrainer(StarterData.TRAINERS.get(i), i + 1)) {
                created++;
            }
        }
        if (created > 0) {
            log.info("Seeded {} trainers (password for all: {})", created, TRAINER_PASSWORD);
        }
    }

    private void seedBranches() {
        int added = 0;
        for (BranchSeed seed : StarterData.BRANCHES) {
            if (branchRepository.existsByNameIgnoreCase(seed.name())) {
                continue;
            }
            branchRepository.save(new Branch(seed.name(), seed.address(), seed.city(), seed.latitude(),
                    seed.longitude(), seed.phone(), seed.opens(), seed.closes()));
            added++;
        }
        if (added > 0) {
            log.info("Seeded {} branch(es)", added);
        }
    }

    private void seedAdmin() {
        if (userRepository.existsByEmailIgnoreCase("admin@gym.com")) {
            return;
        }
        Admin admin = new Admin("Gym Admin", "admin@gym.com", "+962790000000", passwordEncoder.encode("Admin1234"));
        admin.markVerified();
        userRepository.save(admin);
        log.info("Seeded admin: admin@gym.com / Admin1234");
    }

    /**
     * Creates the trainer if missing, then fills in anything still empty:
     * branch, hourly rate, profile (category, gender, languages, tags, certificates) and weekly schedule.
     * Trainers created before this version (Sara, Omar, Lina) get their new profile fields here.
     *
     * @return true if a new trainer account was created
     */
    private boolean seedTrainer(TrainerSeed seed, int number) {
        boolean created = false;

        // 1. Find or create the trainer
        Trainer trainer = userRepository.findByEmailIgnoreCase(seed.email())
                .filter(user -> user instanceof Trainer)       // only if that account really is a trainer
                .map(user -> (Trainer) user)
                .orElse(null);

        if (trainer == null) {
            if (userRepository.existsByEmailIgnoreCase(seed.email())) {
                log.warn("{} exists but is not a trainer, skipping", seed.email());
                return false;
            }
            String phone = String.format("+96279%07d", number);   // +962790000001, +962790000002 ...
            trainer = new Trainer(seed.name(), seed.email(), phone, passwordEncoder.encode(TRAINER_PASSWORD),
                    seed.specialty(), seed.bio(), seed.years());
            trainer.markVerified();
            trainer = userRepository.save(trainer);
            created = true;
        }

        // 2. Branch
        if (trainer.getBranch() == null) {
            branchRepository.findByNameIgnoreCase(seed.branchName()).ifPresent(trainer::assignToBranch);
        }

        // 3. Price per hour
        if (!trainer.hasHourlyRate()) {
            trainer.changeHourlyRate(new BigDecimal(seed.hourlyRate()));
        }

        // 4. Profile shown in the app
        if (!trainer.hasProfile()) {
            trainer.updateProfile(seed.category(), seed.gender(), seed.languages(), seed.tags(), seed.certifications());
        }

        // 5. Weekly schedule
        if (workingHoursRepository.findByTrainerId(trainer.getId()).isEmpty()) {
            final Trainer owner = trainer;
            workingHoursRepository.saveAll(seed.schedule().stream()
                    .map(block -> new WorkingHours(owner, block.day(), block.start(), block.end()))
                    .toList());
        }
        return created;
    }
}
