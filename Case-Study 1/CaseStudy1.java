import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

enum Currency {
    EUR(2), USD(2), JPY(0), KWD(3);

    final int minorUnits;

    Currency(int minorUnits) {
        this.minorUnits = minorUnits;
    }
}

class CurrencyMismatch extends RuntimeException {
    public CurrencyMismatch() {
        super("Currencies do not match");
    }
}

final class Money {
    private final long minor;            // amount in minor units
    private final Currency currency;

    private Money(long minor, Currency currency) {
        if (minor < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        this.minor = minor;
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    // Rejects negatives and excess precision
    static Money of(String decimal, Currency c) {
        Objects.requireNonNull(decimal, "decimal");
        Objects.requireNonNull(c, "currency");

        BigDecimal bd = new BigDecimal(decimal.trim());

        if (bd.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Negative amount not allowed: " + decimal);
        }
        if (bd.scale() > c.minorUnits) {
            throw new IllegalArgumentException(
                "Excess precision for " + c + " (expected at most " + c.minorUnits + " decimal places): " + decimal
            );
        }

        // Convert to minor units (e.g., "12.34" USD -> 1234L) and check for long overflow
        long minorAmount = bd.movePointRight(c.minorUnits).longValueExact();
        return new Money(minorAmount, c);
    }

    Money plus(Money o) {
        requireSame(o);
        return new Money(Math.addExact(minor, o.minor), currency);
    }

    Money times(Quantity q) {
        Objects.requireNonNull(q, "quantity");
        return new Money(Math.multiplyExact(minor, q.value()), currency);
    }

    // Rounds HALF_EVEN, result >= 0
    Money minus(Percentage p) {
        Objects.requireNonNull(p, "percentage");

        // Calculate remaining amount directly: minor * (10000 - bp) / 10000 rounded HALF_EVEN
        BigDecimal remaining = BigDecimal.valueOf(minor)
            .multiply(BigDecimal.valueOf(10_000 - p.basisPoints()))
            .divide(BigDecimal.valueOf(10_000), 0, RoundingMode.HALF_EVEN);

        return new Money(remaining.longValueExact(), currency);
    }

    private void requireSame(Money o) {
        Objects.requireNonNull(o, "other");
        if (currency != o.currency) {
            throw new CurrencyMismatch();
        }
    }

    public long minor() {
        return minor;
    }

    public Currency currency() {
        return currency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Money other)) return false;
        return minor == other.minor && currency == other.currency;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minor, currency);
    }

    @Override
    public String toString() {
        return BigDecimal.valueOf(minor, currency.minorUnits).toPlainString() + " " + currency;
    }
}

final class Percentage {
    private final int basisPoints;       // 0..10000

    private Percentage(int basisPoints) {
        this.basisPoints = basisPoints;
    }

    static Percentage ofBasisPoints(int bp) {
        if (bp < 0 || bp > 10_000) {
            throw new IllegalArgumentException("Basis points must be between 0 and 10000: " + bp);
        }
        return new Percentage(bp);
    }

    public int basisPoints() {
        return basisPoints;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Percentage other)) return false;
        return basisPoints == other.basisPoints;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(basisPoints);
    }

    @Override
    public String toString() {
        return BigDecimal.valueOf(basisPoints, 2).stripTrailingZeros().toPlainString() + "%";
    }
}

final class Quantity {
    private final int value;             // >= 1

    private Quantity(int value) {
        this.value = value;
    }

    static Quantity of(int v) {
        if (v < 1) {
            throw new IllegalArgumentException("Quantity must be >= 1: " + v);
        }
        return new Quantity(v);
    }

    public int value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Quantity other)) return false;
        return value == other.value;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    @Override
    public String toString() {
        return Integer.toString(value);
    }
}

public class CaseStudy1 {
    public static void main(String[] args) {
        Money usd = Money.of("12.34", Currency.USD);
        Money total = usd.plus(Money.of("4.56", Currency.USD));
        Money discounted = Money.of("1000.00", Currency.USD)
            .minus(Percentage.ofBasisPoints(2500));
        Money qty = Money.of("2.50", Currency.USD).times(Quantity.of(3));

        System.out.println("usd = " + usd);
        System.out.println("total = " + total);
        System.out.println("discounted = " + discounted);
        System.out.println("qty = " + qty);
    }
}