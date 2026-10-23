import com.wingedsheep.sdk.core.ManaCost;
public class ArcanaUlalekManaProbe {
    public static void main(String[] args) {
        if (ManaCost.Companion.parse("{C}{C}").getCmc() != 2) throw new AssertionError("Colorless cost changed");
        for (String color : new String[]{"W", "U", "B", "R", "G"}) {
            String symbol = "{C/" + color + "}";
            try {
                ManaCost.Companion.parse(symbol);
                throw new AssertionError("Expected unsupported-symbol reproduction: " + symbol);
            } catch (IllegalArgumentException expected) {
                System.out.println(symbol + ": confirmed parser rejection: " + expected.getMessage());
            }
        }
    }
}
