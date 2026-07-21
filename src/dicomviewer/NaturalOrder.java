//
// NaturalOrder.java
// Case-insensitive, numeric-aware string comparison, matching the web app's
// localeCompare({ numeric: true, sensitivity: 'base' }).
//

package dicomviewer;

import java.util.Comparator;

public final class NaturalOrder implements Comparator<String> {
    public static final NaturalOrder INSTANCE = new NaturalOrder();

    public int compare(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int ia = 0, ib = 0;
        int na = a.length(), nb = b.length();
        while (ia < na && ib < nb) {
            char ca = a.charAt(ia);
            char cb = b.charAt(ib);
            boolean da = Character.isDigit(ca);
            boolean db = Character.isDigit(cb);
            if (da && db) {
                // Compare two runs of digits by numeric value, ignoring leading zeros.
                int sa = ia, sb = ib;
                while (ia < na && Character.isDigit(a.charAt(ia))) ia++;
                while (ib < nb && Character.isDigit(b.charAt(ib))) ib++;
                String na1 = stripLeadingZeros(a.substring(sa, ia));
                String nb1 = stripLeadingZeros(b.substring(sb, ib));
                if (na1.length() != nb1.length()) return na1.length() - nb1.length();
                int cmp = na1.compareTo(nb1);
                if (cmp != 0) return cmp;
            } else {
                char la = Character.toLowerCase(ca);
                char lb = Character.toLowerCase(cb);
                if (la != lb) return la - lb;
                ia++;
                ib++;
            }
        }
        return (na - ia) - (nb - ib);
    }

    private static String stripLeadingZeros(String s) {
        int i = 0;
        while (i < s.length() - 1 && s.charAt(i) == '0') i++;
        return s.substring(i);
    }
}
