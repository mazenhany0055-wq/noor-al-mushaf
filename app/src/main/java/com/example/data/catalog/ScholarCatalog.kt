package com.example.data.catalog

import com.example.domain.model.LessonMediaType
import com.example.domain.model.LessonScholar
import com.example.domain.model.ReligiousLesson

object ScholarCatalog {

    val scholars: List<LessonScholar> = listOf(
        LessonScholar(
            id = "latest",
            arabicName = "الأحدث",
            titleOrSpecialty = "أحدث الدروس المرفوعة تلقائياً من الإنترنت",
            archiveSearchQueries = listOf(
                "(الشعراوي OR \"سمير مصطفى\" OR الحصري OR \"ياسر الدوسري\" OR \"أحمد سمير\" OR \"محمد عزب\" OR \"محمد حسان\" OR \"عمر عبد الكافي\" OR \"عثمان الخميس\")"
            ),
            categoryTag = "تحديث تلقائي"
        ),
        LessonScholar(
            id = "shaarawy",
            arabicName = "الشيخ الشعراوي",
            titleOrSpecialty = "خواطر وتفسير القرآن الكريم (صوت وصورة)",
            archiveSearchQueries = listOf(
                "\"الشعراوي\" OR shaarawy OR alsha3rawi OR sha3rawy"
            ),
            fallbackIdentifiers = listOf(
                "movie-tafseer-alsha3rawi-video-full",
                "Tafsir.Al-Shaarawy.Video",
                "AlShaarawy-Tafseer-Quran"
            ),
            categoryTag = "تفسير القرآن"
        ),
        LessonScholar(
            id = "hussary",
            arabicName = "الشيخ الحصري",
            titleOrSpecialty = "المصحف المعلم وأحكام التجويد والتلاوة",
            archiveSearchQueries = listOf(
                "\"الحصري\" OR hussary OR hosary OR alhosary"
            ),
            fallbackIdentifiers = listOf(
                "mo3allem-128kb-mp3-alhosary-cairosound-1411-without-repeat-of-kids",
                "Al-Husary-Tajweed-Lessons"
            ),
            categoryTag = "التجويد والتلاوة"
        ),
        LessonScholar(
            id = "yasser",
            arabicName = "الشيخ ياسر الدوسري",
            titleOrSpecialty = "خطب المسجد الحرام والدروس الإيمانية",
            archiveSearchQueries = listOf(
                "\"ياسر الدوسري\" OR \"yasser al-dossary\" OR dossary"
            ),
            fallbackIdentifiers = listOf(
                "khutab-haram-makki-yasser-dossary",
                "Yasser-Al-Dossary-Lectures"
            ),
            categoryTag = "خطب الحرم"
        ),
        LessonScholar(
            id = "samir_mustafa",
            arabicName = "الشيخ سمير مصطفى",
            titleOrSpecialty = "السيرة النبوية وشرح الكتب والرقائق",
            archiveSearchQueries = listOf(
                "\"سمير مصطفى\" OR \"samir mostafa\" OR \"samir mustafa\""
            ),
            fallbackIdentifiers = listOf(
                "www.forsanhaq.com_595",
                "islami-888",
                "islami-889",
                "mostafa-mohazarat",
                "20231226_20231226_1901"
            ),
            categoryTag = "السيرة والرقائق"
        ),
        LessonScholar(
            id = "ahmed_samir",
            arabicName = "الشيخ أحمد سمير",
            titleOrSpecialty = "دروس التزكية والمواعظ التربوية",
            archiveSearchQueries = listOf(
                "\"أحمد سمير\" AND (درس OR محاضرة OR خطبة OR دين OR القرآن)"
            ),
            categoryTag = "تربية وتزكية"
        ),
        LessonScholar(
            id = "mohamed_azab",
            arabicName = "الشيخ محمد عزب",
            titleOrSpecialty = "دروس وعظية وقصص وابتهالات",
            archiveSearchQueries = listOf(
                "\"محمد عزب\" OR \"محمد العزب\""
            ),
            categoryTag = "مواعظ وقصص"
        ),
        LessonScholar(
            id = "yaqoub",
            arabicName = "الشيخ محمد حسين يعقوب",
            titleOrSpecialty = "سلاسل إصلاح القلوب والتزكية",
            archiveSearchQueries = listOf(
                "\"محمد حسين يعقوب\" OR yaqoub"
            ),
            categoryTag = "إصلاح القلوب"
        ),
        LessonScholar(
            id = "hassan",
            arabicName = "الشيخ محمد حسان",
            titleOrSpecialty = "السيرة النبوية وأصول الإيمان",
            archiveSearchQueries = listOf(
                "\"محمد حسان\" OR \"mohamed hassan\""
            ),
            categoryTag = "السيرة والعقيدة"
        ),
        LessonScholar(
            id = "abdelkafy",
            arabicName = "الشيخ عمر عبد الكافي",
            titleOrSpecialty = "سلسلة الدار الآخرة وقصص القرآن",
            archiveSearchQueries = listOf(
                "\"عمر عبد الكافي\" OR abdelkafy"
            ),
            categoryTag = "الدار الآخرة"
        ),
        LessonScholar(
            id = "alkhamis",
            arabicName = "الشيخ عثمان الخميس",
            titleOrSpecialty = "العقيدة والفقه والتاريخ الإسلامي",
            archiveSearchQueries = listOf(
                "\"عثمان الخميس\" OR alkhamis"
            ),
            categoryTag = "العقيدة والفقه"
        ),
        LessonScholar(
            id = "nabulsi",
            arabicName = "د. محمد راتب النابلسي",
            titleOrSpecialty = "أسماء الله الحسنى وتربية النفس",
            archiveSearchQueries = listOf(
                "\"النابلسي\" OR nabulsi"
            ),
            categoryTag = "تزكية وتفكر"
        ),
        LessonScholar(
            id = "ahmed_amer",
            arabicName = "د. أحمد عامر",
            titleOrSpecialty = "سيرة النبي ﷺ وقصص الصحابة",
            archiveSearchQueries = listOf(
                "\"أحمد عامر\" AND (السيرة OR درس OR محاضرة)"
            ),
            categoryTag = "السيرة النبوية"
        )
    )

    val starterVerifiedLessons: List<ReligiousLesson> = listOf(
        ReligiousLesson(
            id = "starter_samir_batle_1",
            scholarId = "samir_mustafa",
            scholarName = "الشيخ سمير مصطفى",
            title = "سلسلة المعارك الكبرى — غزوة بدر الكبرى (مرئي)",
            description = "درس مرئي مفصل عن دروس وعبر غزوة بدر الكبرى",
            mediaUrl = "https://archive.org/download/www.forsanhaq.com_595/01.mp4",
            thumbnailUrl = "https://archive.org/services/img/www.forsanhaq.com_595",
            mediaType = LessonMediaType.VIDEO,
            sourceLabel = "Archive.org • فيديو"
        ),
        ReligiousLesson(
            id = "starter_samir_seerah_1",
            scholarId = "samir_mustafa",
            scholarName = "الشيخ سمير مصطفى",
            title = "السيرة النبوية — الدرس الأول: أهمية دراسة السيرة",
            description = "محاضرة شاملة في سيرة النبي صلى الله عليه وسلم",
            mediaUrl = "https://archive.org/download/islami-888/001.mp3",
            thumbnailUrl = "https://archive.org/services/img/islami-888",
            mediaType = LessonMediaType.AUDIO,
            sourceLabel = "Archive.org • صوتي"
        ),
        ReligiousLesson(
            id = "starter_samir_wabil_1",
            scholarId = "samir_mustafa",
            scholarName = "الشيخ سمير مصطفى",
            title = "شرح كتاب الوابل الصيب — فضل الذكر",
            description = "شرح مميز لكتاب الوابل الصيب لابن القيم",
            mediaUrl = "https://archive.org/download/islami-889/001.mp3",
            thumbnailUrl = "https://archive.org/services/img/islami-889",
            mediaType = LessonMediaType.AUDIO,
            sourceLabel = "Archive.org • صوتي"
        ),
        ReligiousLesson(
            id = "starter_hussary_muallim_1",
            scholarId = "hussary",
            scholarName = "الشيخ محمود خليل الحصري",
            title = "المصحف المعلم — سورة الفاتحة وتصحيح التلاوة",
            description = "تعليم مخارج الحروف وأحكام التلاوة الصحيحة بصوت الشيخ الحصري",
            mediaUrl = "https://archive.org/download/mo3allem-128kb-mp3-alhosary-cairosound-1411-without-repeat-of-kids/001.mp3",
            thumbnailUrl = "https://archive.org/services/img/mo3allem-128kb-mp3-alhosary-cairosound-1411-without-repeat-of-kids",
            mediaType = LessonMediaType.AUDIO,
            sourceLabel = "Archive.org • صوتي"
        ),
        ReligiousLesson(
            id = "starter_shaarawy_fatiha",
            scholarId = "shaarawy",
            scholarName = "الشيخ محمد متولي الشعراوي",
            title = "خواطر الإمام الشعراوي — مقدمة تفسير القرآن وسورة الفاتحة",
            description = "تفسير وخواطر الشيخ الشعراوي رحمه الله",
            mediaUrl = "https://archive.org/download/movie-tafseer-alsha3rawi-video-full/001.mp4",
            thumbnailUrl = "https://archive.org/services/img/movie-tafseer-alsha3rawi-video-full",
            mediaType = LessonMediaType.VIDEO,
            sourceLabel = "Archive.org • فيديو"
        ),
        ReligiousLesson(
            id = "starter_alafasy_adhkar",
            scholarId = "yasser",
            scholarName = "الشيخ ناصر القطامي",
            title = "رقائق وأذكار الصباح والمساء — بصوت مؤثر",
            description = "تلاوة وأذكار مختارة تبعث السكينة في القلب",
            mediaUrl = "https://archive.org/download/way2sona_20160320_2316/%D8%A3%D8%B0%D9%83%D8%A7%D8%B1%20%D8%A7%D9%84%D8%B5%D8%A8%D8%A7%D8%AD%20%D9%88%D8%A7%D9%84%D9%85%D8%B3%D8%A7%D8%A1%20%D8%A8%D8%B5%D9%88%D8%AA%20%D8%A7%D9%84%D8%B4%D9%8A%D8%AE%20%D9%86%D8%A7%D8%B5%D8%B1%20%D8%A7%D9%84%D9%82%D8%B7%D8%A7%D9%85%D9%8A.mp3",
            thumbnailUrl = "https://archive.org/services/img/way2sona_20160320_2316",
            mediaType = LessonMediaType.AUDIO,
            sourceLabel = "Archive.org • صوتي"
        )
    )
}
