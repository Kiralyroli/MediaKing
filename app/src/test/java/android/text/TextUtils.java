package android.text;

/**
 * JVM stand-in for the two TextUtils methods Media3's Format/MimeTypes call, so they can be used
 * in plain unit tests (the android.jar stubs throw "not mocked").
 */
public class TextUtils {
    public static boolean isEmpty(CharSequence str) {
        return str == null || str.length() == 0;
    }

    public static boolean equals(CharSequence a, CharSequence b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.toString().contentEquals(b);
    }
}
