package me.thetealviper.commandshop.commands;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Safely normalizes only CommandShop-owned command roots. Arguments are
 * deliberately preserved byte-for-byte for player names and future extensions.
 */
public final class CommandCaseNormalizer {
    private static final Pattern COMMAND_ROOT = Pattern.compile(
            "^(?<slash>/)?(?:(?<namespace>commandshop):)?"
            + "(?<command>commandshop|cshop|shop-gui|buy-gui|sell-gui|"
            + "shop|buy|sell|price|setprice)(?=\\s|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private CommandCaseNormalizer() {
    }

    public static String normalize(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        Matcher matcher = COMMAND_ROOT.matcher(input);
        if (!matcher.find()) {
            return input;
        }
        StringBuilder normalized = new StringBuilder(input.length());
        if (matcher.group("slash") != null) {
            normalized.append('/');
        }
        String namespace = matcher.group("namespace");
        if (namespace != null) {
            normalized.append(namespace.toLowerCase(Locale.ROOT)).append(':');
        }
        normalized.append(matcher.group("command").toLowerCase(Locale.ROOT));
        normalized.append(input, matcher.end(), input.length());
        return normalized.toString();
    }
}
