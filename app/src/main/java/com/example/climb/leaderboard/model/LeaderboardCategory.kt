package com.example.climb.leaderboard.model

/** Exactly five categories — do not add unrelated ones. */
enum class LeaderboardCategory(
    val tabTitle: String,
    val podiumTitle: String,
    val scoringExplanation: String,
) {
    OVERALL(
        tabTitle = "Overall",
        podiumTitle = "Weekly Leaders",
        scoringExplanation = "Your five best unique sends, consistency and quality sessions contribute to your overall weekly score.",
    ),
    V_GRADE(
        tabTitle = "V Grade",
        podiumTitle = "Hardest Sends",
        scoringExplanation = "Harder completed climbs rank higher. Ties are broken using your top-three average and number of attempts.",
    ),
    CONSISTENCY(
        tabTitle = "Consistency",
        podiumTitle = "Most Consistent",
        scoringExplanation = "Consistency measures how many unique attempted problems you completed. At least five attempted problems are required.",
    ),
    SESSIONS(
        tabTitle = "Sessions",
        podiumTitle = "Most Active",
        scoringExplanation = "Active days are ranked first. Quality sessions and completed problems break ties.",
    ),
    SENDS(
        tabTitle = "Sends",
        podiumTitle = "Most Sends",
        scoringExplanation = "Each unique completed problem earns grade-based points. Flashes and second-attempt sends receive bonuses.",
    ),
}
