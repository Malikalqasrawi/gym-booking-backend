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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adds any missing {@link StarterData} at startup: the branches, the first admin (once a password is
 * configured) and, only when app.seed.demo-trainers is on, the demo trainers. Existing records are
 * left untouched, so it is safe to run on every start.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);
    private static final String ADMIN_EMAIL = "admin@gym.com";
    /** Demo trainers exist only for local testing; their shared password is in the README. */
    private static final String DEMO_TRAINER_PASSWORD = "Trainer1234";

    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminInitialPassword;
    private final boolean seedDemoTrainers;

    public DataSeeder(UserRepository userRepository,
                      BranchRepository branchRepository,
                      WorkingHoursRepository workingHoursRepository,
                      PasswordEncoder passwordEncoder,
                      @Value("${app.admin.initial-password:}") String adminInitialPassword,
                      @Value("${app.seed.demo-trainers:false}") boolean seedDemoTrainers) {
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.workingHoursRepository = workingHoursRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminInitialPassword = adminInitialPassword;
        this.seedDemoTrainers = seedDemoTrainers;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedBranches();   // trainers are assigned to branches, so these go first
        seedAdmin();
        if (!seedDemoTrainers) {
            return;
        }

        int created = 0;
        for (int i = 0; i < StarterData.TRAINERS.size(); i++) {
            if (seedTrainer(StarterData.TRAINERS.get(i), i + 1)) {
                created++;
            }
        }
        if (created > 0) {
            log.info("Seeded {} demo trainers (see the README for their login)", created);
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

    /**
     * Creates the first admin account with app.admin.initial-password (set in local.properties or
     * .env). Without one, no admin is created, so a working admin password is never in the code or
     * the log.
     */
    private void seedAdmin() {
        if (userRepository.existsByEmailIgnoreCase(ADMIN_EMAIL)) {
            return;
        }
        if (adminInitialPassword.isBlank()) {
            log.warn("There is no admin account yet. Set app.admin.initial-password (ADMIN_PASSWORD for Docker) "
                    + "and restart to create {}.", ADMIN_EMAIL);
            return;
        }
        Admin admin = new Admin("Gym Admin", ADMIN_EMAIL, "+962790000000", passwordEncoder.encode(adminInitialPassword));
        admin.markVerified();
        userRepository.save(admin);
        log.info("Created the admin account {} with the password from app.admin.initial-password", ADMIN_EMAIL);
    }

    /**
     * Creates the trainer with branch, rate, profile and schedule if no account uses the email.
     * Existing trainers are left alone, so changes made by admins survive restarts.
     *
     * @return true if a new trainer account was created
     */
    private boolean seedTrainer(TrainerSeed seed, int number) {
        if (userRepository.existsByEmailIgnoreCase(seed.email())) {
            return false;
        }
        String phone = String.format("+96279%07d", number);
        Trainer trainer = new Trainer(seed.name(), seed.email(), phone, passwordEncoder.encode(DEMO_TRAINER_PASSWORD),
                seed.specialty(), seed.bio(), seed.years());
        trainer.markVerified();
        branchRepository.findByNameIgnoreCase(seed.branchName()).ifPresent(trainer::assignToBranch);
        trainer.changeHourlyRate(new BigDecimal(seed.hourlyRate()));
        trainer.updateProfile(seed.category(), seed.gender(), seed.languages(), seed.tags(), seed.certifications());
        Trainer saved = userRepository.save(trainer);

        workingHoursRepository.saveAll(seed.schedule().stream()
                .map(block -> new WorkingHours(saved, block.day(), block.start(), block.end()))
                .toList());
        return true;
    }
}
