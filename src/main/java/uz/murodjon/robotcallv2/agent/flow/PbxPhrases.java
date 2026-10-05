package uz.murodjon.robotcallv2.agent.flow;

import uz.murodjon.robotcallv2.agent.tts.SpokenNumbers;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * What the PBX side of a call says — queue announcements, menu retries, voicemail, the
 * survey, and "we are closed, we open tomorrow at nine" (docs/INBOUND-PBX-PLAN.md §5.3,
 * §6, §7.3). Numbers are written out in words here, because a prompt read by TTS must
 * say "bir yuz yigirma to'qqizinchi", not spell "129". Uzbek unless the call is Russian
 * or English.
 */
public final class PbxPhrases {

    private static final String[] UZ_DAYS = {
            "dushanba", "seshanba", "chorshanba", "payshanba", "juma", "shanba", "yakshanba"
    };
    private static final String[] RU_DAYS_ACCUSATIVE = {
            "в понедельник", "во вторник", "в среду", "в четверг", "в пятницу", "в субботу", "в воскресенье"
    };
    private static final String[] UZ_MONTHS = {
            "yanvar", "fevral", "mart", "aprel", "may", "iyun",
            "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr"
    };
    private static final String[] RU_MONTHS_GENITIVE = {
            "января", "февраля", "марта", "апреля", "мая", "июня",
            "июля", "августа", "сентября", "октября", "ноября", "декабря"
    };
    /** Beyond this many days ahead the opening day is named by date, not weekday. */
    private static final int WEEKDAY_HORIZON_DAYS = 6;

    private PbxPhrases() {
    }

    /**
     * "Hozir ish vaqtimizdan tashqari. Ertaga soat to'qqizda ishni boshlaymiz." — built from
     * the schedule itself, so the words never go stale when the hours change.
     *
     * @param nextOpening null when nothing opens within the look-ahead window
     * @param holidayName today's holiday, or null
     */
    public static String afterHours(String language, ZonedDateTime now, ZonedDateTime nextOpening, String holidayName) {
        StringBuilder text = new StringBuilder();
        if (isRussian(language)) {
            if (holidayName != null) {
                text.append("Сегодня праздничный день — ").append(holidayName).append(". ");
            }
            text.append("Сейчас нерабочее время.");
            if (nextOpening != null) {
                String time = SpokenNumbers.clockTime(nextOpening.toLocalTime(), language);
                long days = daysBetween(now, nextOpening);
                if (days == 0) {
                    text.append(" Мы продолжим работу сегодня в ").append(time).append('.');
                } else if (days == 1) {
                    text.append(" Мы начнём работу завтра в ").append(time).append('.');
                } else if (days <= WEEKDAY_HORIZON_DAYS) {
                    text.append(" Мы начнём работу ").append(RU_DAYS_ACCUSATIVE[dayIndex(nextOpening.getDayOfWeek())])
                            .append(" в ").append(time).append('.');
                } else {
                    text.append(" Мы начнём работу ").append(nextOpening.getDayOfMonth()).append(' ')
                            .append(RU_MONTHS_GENITIVE[nextOpening.getMonthValue() - 1]).append(" в ").append(time).append('.');
                }
            }
            return text.toString();
        }
        if (isEnglish(language)) {
            text.append("We are closed right now.");
            if (nextOpening != null) {
                text.append(" We open again at ").append(SpokenNumbers.clockTime(nextOpening.toLocalTime(), language))
                        .append(daysBetween(now, nextOpening) == 1 ? " tomorrow." : ".");
            }
            return text.toString();
        }
        if (holidayName != null) {
            text.append("Bugun ").append(holidayName).append(" munosabati bilan dam olish kuni. ");
        }
        text.append("Hozir ish vaqtimizdan tashqari.");
        if (nextOpening != null) {
            String time = uzbekClockLocative(nextOpening.toLocalTime());
            long days = daysBetween(now, nextOpening);
            if (days == 0) {
                text.append(" Bugun soat ").append(time).append(" ishni davom ettiramiz.");
            } else if (days == 1) {
                text.append(" Ertaga soat ").append(time).append(" ishni boshlaymiz.");
            } else if (days <= WEEKDAY_HORIZON_DAYS) {
                text.append(' ').append(capitalize(UZ_DAYS[dayIndex(nextOpening.getDayOfWeek())]))
                        .append(" kuni soat ").append(time).append(" ishni boshlaymiz.");
            } else {
                text.append(' ').append(capitalize(SpokenNumbers.ordinal(nextOpening.getDayOfMonth(), language)))
                        .append(' ').append(UZ_MONTHS[nextOpening.getMonthValue() - 1])
                        .append(" kuni soat ").append(time).append(" ishni boshlaymiz.");
            }
        }
        return text.toString();
    }

    /** "Sizning navbatingiz — to'rtinchi. Taxminiy kutish vaqti — besh daqiqa." */
    public static String queuePosition(int position, Integer waitMinutes, String language) {
        if (isRussian(language)) {
            String head = position <= 1 ? "Вы первый в очереди."
                    : "Ваш номер в очереди — " + SpokenNumbers.ordinal(position, language) + ".";
            if (waitMinutes == null) {
                return head;
            }
            return head + (waitMinutes <= 1 ? " Примерное время ожидания — меньше минуты."
                    : " Примерное время ожидания — " + russianMinutes(waitMinutes) + ".");
        }
        if (isEnglish(language)) {
            String head = "You are number " + position + " in the queue.";
            return waitMinutes == null ? head : head + " The expected wait is about " + waitMinutes + " minutes.";
        }
        String head = position <= 1 ? "Siz navbatda birinchisiz."
                : "Sizning navbatingiz — " + SpokenNumbers.ordinal(position, language) + ".";
        if (waitMinutes == null) {
            return head;
        }
        return head + (waitMinutes <= 1 ? " Taxminiy kutish vaqti — bir daqiqadan kam."
                : " Taxminiy kutish vaqti — " + SpokenNumbers.cardinal(waitMinutes, language) + " daqiqa.");
    }

    public static String allOperatorsBusy(String language) {
        return pick(language,
                "Barcha operatorlarimiz hozir band. Iltimos, kuting — sizga tez orada javob beramiz.",
                "Все операторы сейчас заняты. Пожалуйста, оставайтесь на линии.",
                "All our operators are busy. Please stay on the line.");
    }

    public static String callbackOffer(String language) {
        return pick(language,
                "Navbatda kutmasdan, sizga qayta qo'ng'iroq qilishimizni xohlasangiz, 1 ni bosing.",
                "Если хотите, чтобы мы перезвонили вам, не дожидаясь в очереди, нажмите 1.",
                "To get a call back instead of waiting, press 1.");
    }

    public static String callbackAccepted(String language) {
        return pick(language,
                "Rahmat. Operatorimiz sizga imkon qadar tezroq qayta qo'ng'iroq qiladi.",
                "Спасибо. Оператор перезвонит вам в ближайшее время.",
                "Thank you. An operator will call you back shortly.");
    }

    public static String overflow(String language) {
        return pick(language,
                "Afsuski, hozir barcha operatorlarimiz band. Sizga qayta qo'ng'iroq qilamiz.",
                "К сожалению, все операторы сейчас заняты. Мы вам перезвоним.",
                "Unfortunately all our operators are busy. We will call you back.");
    }

    public static String invalidOption(String language) {
        return pick(language, "Noto'g'ri tanlov.", "Неверный выбор.", "That is not an option.");
    }

    public static String noInput(String language) {
        return pick(language, "Siz hech narsa tanlamadingiz.", "Вы ничего не выбрали.", "Nothing was selected.");
    }

    public static String voicemailGreeting(String language) {
        return pick(language,
                "Iltimos, signaldan keyin xabaringizni qoldiring. Tugatgach, panjara belgisini bosing yoki go'shakni qo'ying.",
                "Пожалуйста, оставьте сообщение после сигнала. Чтобы закончить, нажмите решётку или положите трубку.",
                "Please leave a message after the tone. Press hash or hang up when you are done.");
    }

    public static String voicemailSaved(String language) {
        return pick(language, "Xabaringiz qabul qilindi. Rahmat!", "Ваше сообщение принято. Спасибо!",
                "Your message has been saved. Thank you!");
    }

    public static String callbackQuestion(String language) {
        return pick(language,
                "Sizga qayta qo'ng'iroq qilishimizni xohlasangiz, 1 ni bosing.",
                "Если хотите, чтобы мы вам перезвонили, нажмите 1.",
                "Press 1 if you would like us to call you back.");
    }

    public static String survey(String language) {
        return pick(language,
                "Xizmatimizni baholang: a'lo bo'lsa 5 ni, yomon bo'lsa 1 ni bosing.",
                "Оцените, пожалуйста, наше обслуживание: отлично — 5, плохо — 1.",
                "Please rate our service from 1 to 5 on your keypad.");
    }

    public static String surveyThanks(String language) {
        return pick(language, "Bahoyingiz uchun rahmat. Xayr!", "Спасибо за оценку. До свидания!",
                "Thank you for your rating. Goodbye!");
    }

    /** Said to whoever answers a forwarded call before it is connected. */
    public static String forwardConfirm(String language) {
        return pick(language,
                "Kompaniyaga kiruvchi qo'ng'iroq. Qabul qilish uchun 1 ni bosing.",
                "Входящий звонок компании. Нажмите 1, чтобы принять.",
                "Incoming company call. Press 1 to accept.");
    }

    /** Said to the operator before a callback dials the client. */
    public static String callingClient(String phone, String language) {
        return pick(language, "Mijozga qo'ng'iroq qilinmoqda: " + phone + ".",
                "Звоним клиенту: " + phone + ".", "Calling the client: " + phone + ".");
    }

    public static String clientDidNotAnswer(String language) {
        return pick(language, "Mijoz javob bermadi.", "Клиент не ответил.", "The client did not answer.");
    }

    /**
     * What the operator hears before the caller is put through (warm transfer, §7.4):
     * short — a whisper over 25 seconds loses the listener.
     */
    public static String operatorWhisper(String language, String queueName, String callerNumber, String clientName,
                                         boolean vip, int waitSeconds, String choices) {
        StringBuilder text = new StringBuilder();
        if (isRussian(language)) {
            text.append("Входящий звонок");
            if (queueName != null) {
                text.append(", очередь ").append(queueName);
            }
            text.append('.');
            if (vip) {
                text.append(" VIP-клиент.");
            }
            if (clientName != null) {
                text.append(" Клиент: ").append(clientName).append('.');
            }
            if (choices != null && !choices.isBlank()) {
                text.append(" Выбрал: ").append(choices).append('.');
            }
            if (waitSeconds >= 60) {
                text.append(" Ждал ").append(russianMinutes(waitSeconds / 60)).append('.');
            }
            return text.toString();
        }
        text.append("Kiruvchi qo'ng'iroq");
        if (queueName != null) {
            text.append(", navbat: ").append(queueName);
        }
        text.append('.');
        if (vip) {
            text.append(" VIP mijoz.");
        }
        if (clientName != null) {
            text.append(" Mijoz: ").append(clientName).append('.');
        } else if (callerNumber != null) {
            text.append(" Raqam: ").append(callerNumber).append('.');
        }
        if (choices != null && !choices.isBlank()) {
            text.append(" Tanlovi: ").append(choices).append('.');
        }
        if (waitSeconds >= 60) {
            text.append(' ').append(capitalize(SpokenNumbers.cardinal(waitSeconds / 60, "uz-UZ")))
                    .append(" daqiqa kutdi.");
        }
        return text.toString();
    }

    /** "soat to'qqizda", "soat o'n to'rt o'ttizda". */
    private static String uzbekClockLocative(LocalTime time) {
        return SpokenNumbers.clockTime(time, "uz-UZ") + "da";
    }

    /** "две минуты", "пять минут", "двадцать одна минута". */
    private static String russianMinutes(int minutes) {
        String words = SpokenNumbers.cardinal(minutes, "ru-RU");
        if (words.endsWith("один")) {
            words = words.substring(0, words.length() - "один".length()) + "одна";
        } else if (words.endsWith("два")) {
            words = words.substring(0, words.length() - "два".length()) + "две";
        }
        int lastTwo = minutes % 100;
        int last = minutes % 10;
        String unit = lastTwo >= 11 && lastTwo <= 14 ? "минут"
                : last == 1 ? "минута"
                : last >= 2 && last <= 4 ? "минуты"
                : "минут";
        return words + " " + unit;
    }

    private static long daysBetween(ZonedDateTime now, ZonedDateTime next) {
        LocalDate today = now.withZoneSameInstant(next.getZone()).toLocalDate();
        return java.time.temporal.ChronoUnit.DAYS.between(today, next.toLocalDate());
    }

    private static int dayIndex(DayOfWeek day) {
        return day.getValue() - 1;
    }

    private static String pick(String language, String uzbek, String russian, String english) {
        if (isRussian(language)) {
            return russian;
        }
        if (isEnglish(language)) {
            return english;
        }
        return uzbek;
    }

    private static boolean isRussian(String language) {
        return language != null && language.toLowerCase().startsWith("ru");
    }

    private static boolean isEnglish(String language) {
        return language != null && language.toLowerCase().startsWith("en");
    }

    /** For a suspended company and for one out of balance alike: the caller is told neither. */
    public static String companyUnavailable(String language) {
        return pick(language,
                "Kechirasiz, xizmat ko'rsatish vaqtincha to'xtatilgan.",
                "Извините, обслуживание временно приостановлено.",
                "Sorry, the service is temporarily unavailable.");
    }

    private static String capitalize(String text) {
        return text == null || text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
