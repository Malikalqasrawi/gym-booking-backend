package com.mycompany.gymbooking.config;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.MONDAY;
import static java.time.DayOfWeek.SATURDAY;
import static java.time.DayOfWeek.SUNDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;

import com.mycompany.gymbooking.model.Gender;
import com.mycompany.gymbooking.model.TrainingCategory;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Starter branches and trainers used by {@link DataSeeder}. All people are fictional.
 * Jordan's weekend is Friday and Saturday, so most schedules run Sunday to Thursday.
 */
final class StarterData {

    private StarterData() {
    }

    record BranchSeed(String name, String address, String city, double latitude, double longitude,
                      String phone, LocalTime opens, LocalTime closes) {
    }

    record Block(DayOfWeek day, LocalTime start, LocalTime end) {
    }

    record TrainerSeed(String name, String email, Gender gender, String branchName,
                       TrainingCategory category, String specialty, String bio, int years, String hourlyRate,
                       String languages, List<String> tags, List<String> certifications, List<Block> schedule) {
    }

    static final List<BranchSeed> BRANCHES = List.of(
            new BranchSeed("Abdoun Branch", "Abdoun Circle, Cairo Street", "Amman",
                    31.9454, 35.8818, "+96265000001", LocalTime.of(6, 0), LocalTime.of(23, 0)),
            new BranchSeed("Khalda Branch", "Wasfi Al-Tal Street (Gardens)", "Amman",
                    31.9975, 35.8337, "+96265000002", LocalTime.of(6, 0), LocalTime.of(22, 0)),
            new BranchSeed("Sweifieh Branch", "Wakalat Street", "Amman",
                    31.9566, 35.8593, "+96265000003", LocalTime.of(7, 0), LocalTime.of(23, 30)),
            new BranchSeed("Shmeisani Branch", "Abdul Hamid Sharaf Street", "Amman",
                    31.9670, 35.9170, "+96265000004", LocalTime.of(5, 30), LocalTime.of(23, 0)),
            new BranchSeed("Jubeiha Branch", "Talaini Street, near the University of Jordan", "Amman",
                    32.0206, 35.8950, "+96265000005", LocalTime.of(6, 0), LocalTime.of(22, 30))
    );

    private static final List<DayOfWeek> SUN_TO_THU = List.of(SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY);

    static final List<TrainerSeed> TRAINERS = List.of(

            new TrainerSeed("Sara Haddad", "sara.trainer@gym.com", Gender.FEMALE, "Abdoun Branch",
                    TrainingCategory.STRENGTH, "Strength & conditioning",
                    "Certified strength coach who loves helping beginners.", 6, "20",
                    "Arabic, English", List.of("Strength", "Beginners", "Weight loss"),
                    List.of("NASM Certified Personal Trainer (CPT)", "Precision Nutrition Level 1"),
                    hours(8, 16, SUN_TO_THU)),

            new TrainerSeed("Yousef Al-Masri", "yousef.trainer@gym.com", Gender.MALE, "Abdoun Branch",
                    TrainingCategory.BOXING, "Boxing & fitness",
                    "Former amateur boxer. Sessions mix technique, pad work and conditioning.", 8, "25",
                    "Arabic, English", List.of("Boxing", "Conditioning", "Self-defense"),
                    List.of("Certified Boxing Fitness Coach", "First Aid & CPR"),
                    join(hours(16, 22, SUNDAY, TUESDAY, THURSDAY), hours(12, 20, SATURDAY))),

            new TrainerSeed("Rania Qasem", "rania.trainer@gym.com", Gender.FEMALE, "Abdoun Branch",
                    TrainingCategory.YOGA, "Vinyasa yoga",
                    "Flowing, breath-led classes for every level. Great for stress and flexibility.", 7, "22",
                    "Arabic, English, French", List.of("Vinyasa", "Flexibility", "Stress relief"),
                    List.of("RYT-500 (Yoga Alliance)"),
                    join(hours(7, 12, MONDAY, WEDNESDAY), hours(17, 20, MONDAY, WEDNESDAY), hours(9, 13, SATURDAY))),

            new TrainerSeed("Khaled Obeidat", "khaled.trainer@gym.com", Gender.MALE, "Abdoun Branch",
                    TrainingCategory.CROSSFIT, "CrossFit & functional training",
                    "High-energy functional workouts: lifting, gymnastics and conditioning.", 5, "20",
                    "Arabic, English", List.of("CrossFit", "Olympic lifting", "Endurance"),
                    List.of("CrossFit Level 2 Trainer (CF-L2)"),
                    join(hours(6, 11, SUNDAY, MONDAY, TUESDAY, WEDNESDAY), hours(8, 14, SATURDAY))),

            new TrainerSeed("Maya Khoury", "maya.trainer@gym.com", Gender.FEMALE, "Abdoun Branch",
                    TrainingCategory.REHAB, "Post-injury rehab & mobility",
                    "Physiotherapist helping people get back to training safely after injuries.", 10, "30",
                    "Arabic, English", List.of("Injury recovery", "Posture", "Seniors"),
                    List.of("BSc Physiotherapy", "NASM Corrective Exercise Specialist (CES)"),
                    hours(10, 18, SUNDAY, TUESDAY, THURSDAY)),

            new TrainerSeed("Omar Khalil", "omar.trainer@gym.com", Gender.MALE, "Khalda Branch",
                    TrainingCategory.YOGA, "Yoga & mobility",
                    "Yoga instructor focused on flexibility and recovery.", 4, "18",
                    "Arabic, English", List.of("Mobility", "Recovery", "Beginners"),
                    List.of("RYT-200 (Yoga Alliance)"),
                    // Thursday runs past Khalda's 22:00 closing; availability is capped at closing time
                    join(hours(14, 22, SUNDAY, TUESDAY), hours(16, 23, THURSDAY), hours(10, 18, SATURDAY))),

            new TrainerSeed("Dana Saleh", "dana.trainer@gym.com", Gender.FEMALE, "Khalda Branch",
                    TrainingCategory.HIIT, "HIIT & weight loss",
                    "Short, intense sessions that burn fat and build fitness fast.", 4, "16",
                    "Arabic, English", List.of("HIIT", "Fat loss", "Women's fitness"),
                    List.of("ACE Certified Personal Trainer"),
                    hours(7, 13, SUN_TO_THU)),

            new TrainerSeed("Ahmad Tarawneh", "ahmad.trainer@gym.com", Gender.MALE, "Khalda Branch",
                    TrainingCategory.STRENGTH, "Bodybuilding & muscle gain",
                    "Nine years of coaching muscle gain, with nutrition plans that fit Jordanian food.", 9, "22",
                    "Arabic, English", List.of("Muscle gain", "Nutrition", "Competition prep"),
                    List.of("NSCA Certified Personal Trainer (NSCA-CPT)", "ISSA Bodybuilding Specialist"),
                    hours(16, 22, SUN_TO_THU)),

            new TrainerSeed("Hala Suleiman", "hala.trainer@gym.com", Gender.FEMALE, "Khalda Branch",
                    TrainingCategory.PILATES, "Mat & reformer Pilates",
                    "Pilates for core strength, posture, and before or after pregnancy.", 6, "21",
                    "Arabic, English", List.of("Reformer", "Core", "Pre & post-natal"),
                    List.of("STOTT PILATES Certified Instructor"),
                    hours(8, 14, MONDAY, WEDNESDAY, SATURDAY)),

            new TrainerSeed("Lina Nasser", "lina.trainer@gym.com", Gender.FEMALE, "Sweifieh Branch",
                    TrainingCategory.PILATES, "Pilates & core",
                    "Pilates instructor who builds core strength and better posture.", 5, "22",
                    "Arabic, English", List.of("Core", "Posture", "Back pain"),
                    List.of("BASI Pilates Comprehensive Certificate"),
                    join(hours(7, 13, MONDAY, WEDNESDAY), hours(17, 21, MONDAY, WEDNESDAY), hours(9, 15, SATURDAY))),

            new TrainerSeed("Faris Al-Zoubi", "faris.trainer@gym.com", Gender.MALE, "Sweifieh Branch",
                    TrainingCategory.HIIT, "Athletic performance & HIIT",
                    "Trains footballers and runners: speed, agility and explosive power.", 7, "20",
                    "Arabic, English", List.of("Speed & agility", "Football", "HIIT"),
                    List.of("NSCA Certified Strength and Conditioning Specialist (CSCS)"),
                    join(hours(15, 22, SUNDAY, TUESDAY, THURSDAY), hours(10, 14, FRIDAY))),

            new TrainerSeed("Noor Hamdan", "noor.trainer@gym.com", Gender.FEMALE, "Sweifieh Branch",
                    TrainingCategory.STRENGTH, "Women's strength training",
                    "Friendly strength sessions for women who are new to the weights area.", 5, "18",
                    "Arabic", List.of("Women only", "Beginners", "Toning"),
                    List.of("ACE Certified Personal Trainer"),
                    hours(9, 15, SUN_TO_THU)),

            new TrainerSeed("Hamza Odeh", "hamza.trainer@gym.com", Gender.MALE, "Sweifieh Branch",
                    TrainingCategory.BOXING, "Kickboxing",
                    "Kickboxing for fitness and confidence. No experience needed.", 6, "22",
                    "Arabic, English", List.of("Kickboxing", "Cardio", "Self-defense"),
                    List.of("Certified Kickboxing Instructor", "First Aid & CPR"),
                    join(hours(17, 23, MONDAY, WEDNESDAY), hours(14, 22, SATURDAY))),

            new TrainerSeed("Jana Haddadin", "jana.trainer@gym.com", Gender.FEMALE, "Sweifieh Branch",
                    TrainingCategory.YOGA, "Prenatal & gentle yoga",
                    "Calm, gentle yoga for pregnancy, beginners and anyone who needs to slow down.", 8, "20",
                    "Arabic, English", List.of("Prenatal", "Beginners", "Breathing"),
                    List.of("RYT-200 (Yoga Alliance)", "Registered Prenatal Yoga Teacher (RPYT)"),
                    join(hours(8, 12, SUNDAY, TUESDAY, THURSDAY), hours(10, 13, SATURDAY))),

            new TrainerSeed("Laith Bataineh", "laith.trainer@gym.com", Gender.MALE, "Shmeisani Branch",
                    TrainingCategory.STRENGTH, "Powerlifting",
                    "Squat, bench and deadlift technique for people who want to get seriously strong.", 11, "28",
                    "Arabic, English", List.of("Powerlifting", "Strength", "Technique"),
                    List.of("NSCA Certified Strength and Conditioning Specialist (CSCS)"),
                    join(hours(6, 10, SUN_TO_THU), hours(17, 21, SUN_TO_THU))),

            new TrainerSeed("Ruba Shahin", "ruba.trainer@gym.com", Gender.FEMALE, "Shmeisani Branch",
                    TrainingCategory.HIIT, "Cardio & spinning",
                    "Upbeat cardio and indoor cycling sessions after work.", 3, "15",
                    "Arabic, English", List.of("Spinning", "Cardio", "Beginners"),
                    List.of("Schwinn Indoor Cycling Instructor", "ACE Group Fitness Instructor"),
                    join(hours(17, 21, SUN_TO_THU), hours(9, 12, SATURDAY))),

            new TrainerSeed("Qais Khatib", "qais.trainer@gym.com", Gender.MALE, "Shmeisani Branch",
                    TrainingCategory.CROSSFIT, "Functional fitness",
                    "Kettlebells, sleds and bodyweight work to make everyday life easier.", 5, "19",
                    "Arabic, English", List.of("Kettlebells", "Mobility", "Functional"),
                    List.of("CrossFit Level 1 Trainer (CF-L1)"),
                    hours(12, 20, SUN_TO_THU)),

            new TrainerSeed("Aya Mansour", "aya.trainer@gym.com", Gender.FEMALE, "Shmeisani Branch",
                    TrainingCategory.REHAB, "Mobility & stretching",
                    "Loosens up stiff backs and necks, perfect for people who sit at a desk all day.", 4, "18",
                    "Arabic, English", List.of("Mobility", "Desk workers", "Back pain"),
                    List.of("NASM Corrective Exercise Specialist (CES)"),
                    hours(8, 15, MONDAY, WEDNESDAY, SATURDAY)),

            new TrainerSeed("Mohammad Nsour", "mohammad.trainer@gym.com", Gender.MALE, "Jubeiha Branch",
                    TrainingCategory.STRENGTH, "Student fitness & strength",
                    "Budget-friendly coaching for university students who want to start lifting.", 3, "14",
                    "Arabic, English", List.of("Beginners", "Muscle gain", "Students"),
                    List.of("ISSA Certified Personal Trainer"),
                    hours(14, 22, SUN_TO_THU)),

            new TrainerSeed("Leen Awad", "leen.trainer@gym.com", Gender.FEMALE, "Jubeiha Branch",
                    TrainingCategory.YOGA, "Hatha yoga & flexibility",
                    "Slow, steady Hatha yoga to build flexibility and focus before exams.", 4, "16",
                    "Arabic, English", List.of("Hatha", "Flexibility", "Stress relief"),
                    List.of("RYT-200 (Yoga Alliance)"),
                    join(hours(7, 13, SUNDAY, TUESDAY, THURSDAY), hours(16, 20, SATURDAY))),

            new TrainerSeed("Bashar Hijazi", "bashar.trainer@gym.com", Gender.MALE, "Jubeiha Branch",
                    TrainingCategory.BOXING, "Boxing fundamentals",
                    "Stance, footwork and combinations, taught step by step.", 6, "18",
                    "Arabic", List.of("Boxing", "Footwork", "Cardio"),
                    List.of("Certified Boxing Fitness Coach"),
                    join(hours(16, 22, MONDAY, WEDNESDAY, THURSDAY), hours(15, 20, FRIDAY))),

            new TrainerSeed("Tala Rawashdeh", "tala.trainer@gym.com", Gender.FEMALE, "Jubeiha Branch",
                    TrainingCategory.HIIT, "Bootcamp & HIIT",
                    "Bootcamp-style circuits: no two sessions are the same.", 5, "17",
                    "Arabic, English", List.of("Bootcamp", "Fat loss", "Circuits"),
                    List.of("ACE Certified Personal Trainer", "TRX Suspension Training Course"),
                    hours(7, 12, SUN_TO_THU))
    );

    /** One block per day, each from startHour to endHour. */
    private static List<Block> hours(int startHour, int endHour, DayOfWeek... days) {
        return hours(startHour, endHour, List.of(days));
    }

    private static List<Block> hours(int startHour, int endHour, List<DayOfWeek> days) {
        return days.stream()
                .map(day -> new Block(day, LocalTime.of(startHour, 0), LocalTime.of(endHour, 0)))
                .toList();
    }

    @SafeVarargs
    private static List<Block> join(List<Block>... parts) {
        List<Block> all = new ArrayList<>();
        for (List<Block> part : parts) {
            all.addAll(part);
        }
        return all;
    }
}
