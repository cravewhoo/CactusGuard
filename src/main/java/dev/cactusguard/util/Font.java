package dev.cactusguard.util;

/** Converts text to small-caps Unicode ("Hello" -> "ʜᴇʟʟᴏ") for that clean mini-Minecraft look. */
public final class Font {

    private static final String NORMAL = "abcdefghijklmnopqrstuvwxyz";
    private static final String SMALL = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀѕᴛᴜᴠᴡxʏᴢ";

    private Font() {}

    public static String small(String in) {
        StringBuilder sb = new StringBuilder(in.length());
        for (int i = 0; i < in.length(); i++) {
            char c = Character.toLowerCase(in.charAt(i));
            int idx = NORMAL.indexOf(c);
            sb.append(idx >= 0 ? SMALL.charAt(idx) : in.charAt(i));
        }
        return sb.toString();
    }
}
