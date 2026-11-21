package com.example.data.catalog

import com.example.domain.model.LessonScholar

/**
 * Verified official YouTube channels and handles for Islamic scholars.
 * Every video shown in "دروس دين" is fetched dynamically from these official channels
 * via YouTube Data API v3 (or official channel feeds + oEmbed verification) and verified
 * to be real on-camera HD video + real voice with zero static slides or AI TTS.
 */
object ScholarCatalog {

    val scholars: List<LessonScholar> = listOf(
        LessonScholar(
            id = "latest",
            arabicName = "الأحدث",
            titleOrSpecialty = "أحدث الفيديوهات المرفوعة مباشرة من القنوات الرسمية للشيوخ",
            categoryTag = "تحديث تلقائي"
        ),
        LessonScholar(
            id = "shaarawy",
            arabicName = "الشيخ الشعراوي",
            titleOrSpecialty = "القناة الرسمية • خواطر وتفسير القرآن بالصوت والصورة",
            officialChannelIds = listOf("UCijSEIUA72pyzSNOf_lx5ng"),
            officialHandles = listOf("alsharawiofficial"),
            youtubeVideoSearchQueries = listOf("الشيخ محمد متولي الشعراوي تفسير القرآن القناة الرسمية"),
            categoryTag = "القناة الرسمية"
        ),
        LessonScholar(
            id = "samir_mustafa",
            arabicName = "الشيخ سمير مصطفى",
            titleOrSpecialty = "القناة الرسمية • السيرة النبوية وشرح الكتب والرقائق",
            officialChannelIds = listOf("UCch6Y4YgssEzMa4Q5zw4xjw"),
            officialHandles = listOf("samirmoustafa"),
            youtubeVideoSearchQueries = listOf("قناة الشيخ سمير مصطفى الرسمية محاضرة"),
            categoryTag = "القناة الرسمية"
        ),
        LessonScholar(
            id = "yasser",
            arabicName = "الشيخ ياسر الدوسري",
            titleOrSpecialty = "القناة الرسمية • خطب المسجد الحرام والدروس المرئية",
            officialChannelIds = listOf("UClncV9OLPto_MinRzGfjr2g"),
            officialHandles = listOf("Yasseraldosry"),
            youtubeVideoSearchQueries = listOf("الشيخ ياسر الدوسري خطبة المسجد الحرام كاملة"),
            requireVisualAppearanceFilter = true,
            categoryTag = "القناة الرسمية"
        ),
        LessonScholar(
            id = "ahmed_samir",
            arabicName = "الشيخ أحمد سمير",
            titleOrSpecialty = "القناة الرسمية • دروس التزكية والقصص القرآنية",
            officialHandles = listOf("Sh.Ahmed-Samir", "Sh.Ahmed-Samir8"),
            youtubeVideoSearchQueries = listOf("الشيخ أحمد سمير درس محاضرة"),
            categoryTag = "القناة الرسمية"
        ),
        LessonScholar(
            id = "mohamed_azab",
            arabicName = "الشيخ العزب",
            titleOrSpecialty = "القناة الرسمية • خطب ومواعظ مرئية من المسجد",
            officialChannelIds = listOf("UCAhQ6OE0QwGmoIKdQFwTn2w"),
            youtubeVideoSearchQueries = listOf("الشيخ العزب خطبة كاملة"),
            categoryTag = "القناة الرسمية"
        ),
        LessonScholar(
            id = "hussary",
            arabicName = "الشيخ الحصري",
            titleOrSpecialty = "فيديوهات وتلاوات تلفزيونية نادرة يظهر فيها الشيخ الحصري",
            officialHandles = listOf("HosariOfficial"),
            youtubeVideoSearchQueries = listOf(
                "الشيخ محمود خليل الحصري فيديو نادر التلفزيون",
                "الشيخ محمود خليل الحصري نور على نور"
            ),
            requireVisualAppearanceFilter = true,
            categoryTag = "تسجيلات مرئية نادرة"
        ),
        LessonScholar(
            id = "yaqoub",
            arabicName = "الشيخ محمد حسين يعقوب",
            titleOrSpecialty = "القناة الرسمية • دروس إصلاح القلوب والرقائق",
            officialChannelIds = listOf("UCdwmI3CAhNtmtr2Q8n44tlA"),
            youtubeVideoSearchQueries = listOf("الشيخ محمد حسين يعقوب درس كامل"),
            categoryTag = "القناة الرسمية"
        ),
        LessonScholar(
            id = "hassan",
            arabicName = "الشيخ محمد حسان",
            titleOrSpecialty = "القناة الرسمية • سيرة النبي ﷺ والدروس التربوية",
            officialHandles = listOf("القناةالرسميةللشيخالدكتورمحمدح"),
            youtubeVideoSearchQueries = listOf("الشيخ محمد حسان درس كامل القناة الرسمية"),
            categoryTag = "القناة الرسمية"
        ),
        LessonScholar(
            id = "abdelkafy",
            arabicName = "د. عمر عبد الكافي",
            titleOrSpecialty = "القناة الرسمية • الدار الآخرة وقصص القرآن الكريم",
            officialChannelIds = listOf("UCKUOmGXE9Ytlc2EzpGqimtw"),
            officialHandles = listOf("abdelkafytube"),
            youtubeVideoSearchQueries = listOf("الدكتور عمر عبد الكافي درس كامل"),
            categoryTag = "القناة الرسمية"
        ),
        LessonScholar(
            id = "alkhamis",
            arabicName = "الشيخ عثمان الخميس",
            titleOrSpecialty = "القناة الرسمية • العقيدة والسيرة والفقه",
            officialChannelIds = listOf("UCWjCSGhmSGu0VLf2mPFS0Kg"),
            officialHandles = listOf("othmanalkamees"),
            youtubeVideoSearchQueries = listOf("الشيخ عثمان الخميس درس كامل"),
            categoryTag = "القناة الرسمية"
        ),
        LessonScholar(
            id = "nabulsi",
            arabicName = "د. محمد راتب النابلسي",
            titleOrSpecialty = "القناة الرسمية • أسماء الله الحسنى وتفسير القرآن",
            officialHandles = listOf("nabulsiencyclopedia"),
            youtubeVideoSearchQueries = listOf("الدكتور محمد راتب النابلسي درس كامل"),
            categoryTag = "القناة الرسمية"
        ),
        LessonScholar(
            id = "ahmed_amer",
            arabicName = "د. أحمد عامر",
            titleOrSpecialty = "القناة الرسمية • السيرة النبوية ورحلة الدار الآخرة",
            officialHandles = listOf("ahmedamercaller"),
            youtubeVideoSearchQueries = listOf("أحمد عامر السيرة النبوية"),
            categoryTag = "القناة الرسمية"
        )
    )
}
