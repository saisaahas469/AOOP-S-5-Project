import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public class CaseStudy2 {
    private static final class DomainException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        DomainException(String message) {
            super(message);
        }
    }

    private record OrderId(String value) {
        public OrderId {
            if (value == null || value.isBlank()) {
                throw new DomainException("OrderId cannot be blank");
            }
        }
    }

    private record ProductId(String value) {
        public ProductId {
            if (value == null || value.isBlank()) {
                throw new DomainException("ProductId cannot be blank");
            }
        }
    }

    private record Quantity(int value) {
        public Quantity {
            if (value <= 0) {
                throw new DomainException("Quantity must be strictly positive");
            }
        }

        public Quantity add(Quantity other) {
            Objects.requireNonNull(other, "Quantity cannot be null");
            return new Quantity(Math.addExact(this.value, other.value));
        }
    }

    private record Money(BigDecimal amount, Currency currency) {
        public Money {
            Objects.requireNonNull(amount, "Amount cannot be null");
            Objects.requireNonNull(currency, "Currency cannot be null");
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new DomainException("Money amount cannot be negative");
            }
        }

        public static Money zero(Currency currency) {
            return new Money(BigDecimal.ZERO, currency);
        }

        public Money plus(Money other) {
            if (!this.currency.equals(other.currency)) {
                throw new DomainException("Currency mismatch: " + this.currency + " vs " + other.currency);
            }
            return new Money(this.amount.add(other.amount), this.currency);
        }

        public Money multiply(int factor) {
            return new Money(this.amount.multiply(BigDecimal.valueOf(factor)), this.currency);
        }
    }

    private record OrderLine(ProductId product, Money unitPrice, Quantity quantity) {
        public OrderLine {
            Objects.requireNonNull(product, "Product cannot be null");
            Objects.requireNonNull(unitPrice, "Unit price cannot be null");
            Objects.requireNonNull(quantity, "Quantity cannot be null");
        }

        public Money total() {
            return unitPrice.multiply(quantity.value());
        }
    }

    public static void main(String[] args) {
        Currency usd = Currency.getInstance("USD");
        OrderId orderId = new OrderId("O-100");
        Quantity quantity = new Quantity(1).add(new Quantity(1));
        OrderLine line = new OrderLine(
            new ProductId("P-100"),
            new Money(new BigDecimal("12.50"), usd),
            quantity
        );
        Money total = Money.zero(usd).plus(line.total());

        System.out.println("Order " + orderId.value() + " line total: " + total.amount() + " " + usd);
    }
}