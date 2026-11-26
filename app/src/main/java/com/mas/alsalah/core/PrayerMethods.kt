package com.mas.alsalah.core

enum class PrayerMethod(
    val id: String,
    val titleArabic: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaFixedMinutes: Int? = null,
    val asrShadowFactor: Double = 1.0
) {
    EGYPT("EGYPT", "الهيئة المصرية العامة للمساحة", 19.5, 17.5),
    MWL("MWL", "رابطة العالم الإسلامي", 18.0, 17.0),
    UMM_AL_QURA("UMM_AL_QURA", "أم القرى", 18.5, 0.0, 90),
    KARACHI("KARACHI", "جامعة العلوم الإسلامية بكراتشي", 18.0, 18.0),
    ISNA("ISNA", "الجمعية الإسلامية لأمريكا الشمالية", 15.0, 15.0),
    EGYPT_HANAFI("EGYPT_HANAFI", "الهيئة المصرية — المذهب الحنفي", 19.5, 17.5, asrShadowFactor = 2.0);

    fun calculation() = PrayerCalculationMethod(fajrAngle, ishaAngle.takeIf { ishaFixedMinutes == null } ?: 17.0, asrShadowFactor, ishaFixedMinutes)
}
