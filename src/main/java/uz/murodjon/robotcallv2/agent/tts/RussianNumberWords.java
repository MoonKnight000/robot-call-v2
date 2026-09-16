package uz.murodjon.robotcallv2.agent.tts;

/**
 * Writes an integer out in Russian words for speech synthesis.
 * Package-private helper for {@link SpeechTextNormalizer}.
 */
final class RussianNumberWords {

    private static final String[] ONES = {
            "ноль", "один", "два", "три", "четыре", "пять",
            "шесть", "семь", "восемь", "девять"
    };

    private static final String[] TEENS = {
            "десять", "одиннадцать", "двенадцать", "тринадцать", "четырнадцать",
            "пятнадцать", "шестнадцать", "семнадцать", "восемнадцать", "девятнадцать"
    };

    private static final String[] TENS = {
            "", "", "двадцать", "тридцать", "сорок", "пятьдесят",
            "шестьдесят", "семьдесят", "восемьдесят", "девяносто"
    };

    private static final String[] HUNDREDS = {
            "", "сто", "двести", "триста", "четыреста", "пятьсот",
            "шестьсот", "семьсот", "восемьсот", "девятьсот"
    };

    private RussianNumberWords() {
    }

    /** Cardinal representation of a number in Russian (e.g. 1500000 -> "один миллион пятьсот тысяч"). */
    static String cardinal(long n) {
        if (n < 0) {
            return "минус " + cardinal(-n);
        }
        if (n == 0) {
            return ONES[0];
        }
        StringBuilder sb = new StringBuilder();
        long billions = n / 1_000_000_000L;
        long millions = (n % 1_000_000_000L) / 1_000_000L;
        long thousands = (n % 1_000_000L) / 1_000L;
        long remainder = n % 1_000L;

        if (billions > 0) {
            appendGroup(sb, billions, "миллиард", "миллиарда", "миллиардов", false);
        }
        if (millions > 0) {
            appendGroup(sb, millions, "миллион", "миллиона", "миллионов", false);
        }
        if (thousands > 0) {
            appendGroup(sb, thousands, "тысяча", "тысячи", "тысяч", true);
        }
        if (remainder > 0) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(triplet(remainder, false));
        }
        return sb.toString().trim();
    }

    private static final String[] ORDINAL_ONES = {
            "", "первый", "второй", "третий", "четвёртый", "пятый",
            "шестой", "седьмой", "восьмой", "девятый"
    };

    private static final String[] ORDINAL_TEENS = {
            "десятый", "одиннадцатый", "двенадцатый", "тринадцатый", "четырнадцатый",
            "пятнадцатый", "шестнадцатый", "семнадцатый", "восемнадцатый", "девятнадцатый"
    };

    private static final String[] ORDINAL_TENS = {
            "", "", "двадцатый", "тридцатый", "сороковой", "пятидесятый",
            "шестидесятый", "семидесятый", "восьмидесятый", "девяностый"
    };

    private static final String[] ORDINAL_HUNDREDS = {
            "", "сотый", "двухсотый", "трёхсотый", "четырёхсотый", "пятисотый",
            "шестисотый", "семисотый", "восьмисотый", "девятисотый"
    };

    /**
     * Masculine nominative ordinal — "номер в очереди сто двадцать девятый". Only the last
     * word changes form, as in Uzbek; everything before it stays cardinal. Round thousands
     * and above (whose ordinal fuses into one word, "двухтысячный") fall back to the
     * cardinal: no queue gets that long.
     */
    static String ordinal(long n) {
        if (n <= 0) {
            return cardinal(n);
        }
        long lastTwo = n % 100;
        if (lastTwo != 0) {
            String head = n >= 100 ? cardinal(n - lastTwo) + " " : "";
            String tail;
            if (lastTwo < 10) {
                tail = ORDINAL_ONES[(int) lastTwo];
            } else if (lastTwo < 20) {
                tail = ORDINAL_TEENS[(int) lastTwo - 10];
            } else if (lastTwo % 10 == 0) {
                tail = ORDINAL_TENS[(int) (lastTwo / 10)];
            } else {
                tail = TENS[(int) (lastTwo / 10)] + " " + ORDINAL_ONES[(int) (lastTwo % 10)];
            }
            return head + tail;
        }
        long lastThree = n % 1000;
        if (lastThree != 0) {
            String head = n >= 1000 ? cardinal(n - lastThree) + " " : "";
            return head + ORDINAL_HUNDREDS[(int) (lastThree / 100)];
        }
        return n == 1000 ? "тысячный" : cardinal(n);
    }

    /** Digit by digit readout (e.g. for card / contract numbers: "ноль один два..."). */
    static String digits(String run) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < run.length(); i++) {
            char c = run.charAt(i);
            if (c >= '0' && c <= '9') {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(ONES[c - '0']);
            }
        }
        return sb.toString();
    }

    private static void appendGroup(StringBuilder sb, long count, String form1, String form2, String form5, boolean feminine) {
        if (count == 0) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(' ');
        }
        sb.append(triplet(count, feminine)).append(' ');
        long lastTwo = count % 100;
        long last = count % 10;
        if (lastTwo >= 11 && lastTwo <= 19) {
            sb.append(form5);
        } else if (last == 1) {
            sb.append(form1);
        } else if (last >= 2 && last <= 4) {
            sb.append(form2);
        } else {
            sb.append(form5);
        }
    }

    private static String triplet(long n, boolean feminine) {
        StringBuilder sb = new StringBuilder();
        int h = (int) ((n / 100) % 10);
        int t = (int) ((n / 10) % 10);
        int u = (int) (n % 10);

        if (h > 0) {
            sb.append(HUNDREDS[h]);
        }
        if (t == 1) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(TEENS[u]);
            return sb.toString();
        }
        if (t > 1) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            sb.append(TENS[t]);
        }
        if (u > 0) {
            if (!sb.isEmpty()) {
                sb.append(' ');
            }
            if (feminine && u == 1) {
                sb.append("одна");
            } else if (feminine && u == 2) {
                sb.append("две");
            } else {
                sb.append(ONES[u]);
            }
        }
        return sb.toString();
    }
}
