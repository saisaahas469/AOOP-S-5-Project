import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

public class CaseStudy4 {

    // Domain Exception for business rule violations
    public static final class DomainException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        public DomainException(String message) {
            super(message);
        }
    }

    // ==================== VALUE OBJECTS ====================

    public record RoomId(String value) {
        public RoomId {
            if (value == null || value.isBlank()) {
                throw new DomainException("RoomId cannot be null or blank");
            }
        }
    }

    public record ReservationId(String value) {
        public ReservationId {
            if (value == null || value.isBlank()) {
                throw new DomainException("ReservationId cannot be null or blank");
            }
        }
    }

    public record GuestId(String value) {
        public GuestId {
            if (value == null || value.isBlank()) {
                throw new DomainException("GuestId cannot be null or blank");
            }
        }
    }

    public record Money(BigDecimal amount, Currency currency) {
        public Money {
            Objects.requireNonNull(amount, "Amount cannot be null");
            Objects.requireNonNull(currency, "Currency cannot be null");
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                throw new DomainException("Money amount cannot be negative: " + amount);
            }
            amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_EVEN);
        }

        public static Money of(String amount, Currency currency) {
            return new Money(new BigDecimal(amount), currency);
        }

        public static Money zero(Currency currency) {
            return new Money(BigDecimal.ZERO, currency);
        }

        public Money multiply(long factor) {
            if (factor < 0) {
                throw new DomainException("Factor cannot be negative");
            }
            return new Money(this.amount.multiply(BigDecimal.valueOf(factor)), this.currency);
        }

        public Money percentage(int percent) {
            if (percent < 0 || percent > 100) {
                throw new DomainException("Percentage must be between 0 and 100");
            }
            BigDecimal result = this.amount
                .multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), currency.getDefaultFractionDigits(), RoundingMode.HALF_EVEN);
            return new Money(result, this.currency);
        }

        @Override
        public String toString() {
            return amount.toPlainString() + " " + currency.getCurrencyCode();
        }
    }

    /**
     * Half-open date interval [checkIn, checkOut) representing a guest's stay period.
     * Enforces check-in < check-out (at least 1 night) and allows consecutive bookings
     * where one reservation checks out on the exact date the next checks in.
     */
    public record StayPeriod(LocalDate checkIn, LocalDate checkOut) {
        public StayPeriod {
            Objects.requireNonNull(checkIn, "Check-in date cannot be null");
            Objects.requireNonNull(checkOut, "Check-out date cannot be null");
            if (!checkOut.isAfter(checkIn)) {
                throw new DomainException(
                    "Stay must cover at least one night (check-in " + checkIn + " must be before check-out " + checkOut + ")"
                );
            }
        }

        public long nights() {
            return ChronoUnit.DAYS.between(checkIn, checkOut);
        }

        /**
         * Two stays [startA, endA) and [startB, endB) overlap iff startA < endB && startB < endA.
         * If endA.equals(startB), overlapsWith returns false (consecutive stay is allowed).
         */
        public boolean overlapsWith(StayPeriod other) {
            Objects.requireNonNull(other, "Other StayPeriod cannot be null");
            return this.checkIn.isBefore(other.checkOut) && other.checkIn.isBefore(this.checkOut);
        }

        @Override
        public String toString() {
            return "[" + checkIn + " -> " + checkOut + " (" + nights() + " nights)]";
        }
    }

    public record PaymentGuarantee(String authorizationReference, Instant authorizedAt) {
        public PaymentGuarantee {
            if (authorizationReference == null || authorizationReference.isBlank()) {
                throw new DomainException("Payment authorization reference cannot be blank");
            }
            Objects.requireNonNull(authorizedAt, "Authorization timestamp cannot be null");
        }
    }

    public record CancellationPolicy(int fullRefundDaysBeforeCheckIn, int partialRefundPercent) {
        public CancellationPolicy {
            if (fullRefundDaysBeforeCheckIn < 0) {
                throw new DomainException("Full refund days threshold cannot be negative");
            }
            if (partialRefundPercent < 0 || partialRefundPercent > 100) {
                throw new DomainException("Partial refund percentage must be between 0 and 100");
            }
        }

        public Money calculateRefund(Money totalPrice, StayPeriod stayPeriod, LocalDate cancellationDate) {
            Objects.requireNonNull(totalPrice, "Total price cannot be null");
            Objects.requireNonNull(stayPeriod, "Stay period cannot be null");
            Objects.requireNonNull(cancellationDate, "Cancellation date cannot be null");

            long daysUntilCheckIn = ChronoUnit.DAYS.between(cancellationDate, stayPeriod.checkIn());
            if (daysUntilCheckIn >= fullRefundDaysBeforeCheckIn) {
                return totalPrice;
            } else if (daysUntilCheckIn >= 0) {
                return totalPrice.percentage(partialRefundPercent);
            } else {
                return Money.zero(totalPrice.currency());
            }
        }
    }

    // ==================== MAKING ILLEGAL STATES UNREPRESENTABLE ====================

    public sealed interface ReservationState
        permits ReservationState.Requested,
                ReservationState.Confirmed,
                ReservationState.CheckedIn,
                ReservationState.Completed,
                ReservationState.Cancelled {

        record Requested(Instant requestedAt) implements ReservationState {
            public Requested {
                Objects.requireNonNull(requestedAt, "requestedAt cannot be null");
            }
        }

        record Confirmed(PaymentGuarantee guarantee, Instant confirmedAt) implements ReservationState {
            public Confirmed {
                Objects.requireNonNull(guarantee, "Confirmed reservation must have a PaymentGuarantee");
                Objects.requireNonNull(confirmedAt, "confirmedAt cannot be null");
            }
        }

        record CheckedIn(PaymentGuarantee guarantee, Instant checkedInAt) implements ReservationState {
            public CheckedIn {
                Objects.requireNonNull(guarantee, "CheckedIn reservation must retain PaymentGuarantee");
                Objects.requireNonNull(checkedInAt, "checkedInAt cannot be null");
            }
        }

        record Completed(PaymentGuarantee guarantee, Instant checkedInAt, Instant checkedOutAt) implements ReservationState {
            public Completed {
                Objects.requireNonNull(guarantee, "Completed reservation must retain PaymentGuarantee");
                Objects.requireNonNull(checkedInAt, "checkedInAt cannot be null");
                Objects.requireNonNull(checkedOutAt, "checkedOutAt cannot be null");
            }
        }

        record Cancelled(Instant cancelledAt, Money refundAmount, String reason) implements ReservationState {
            public Cancelled {
                Objects.requireNonNull(cancelledAt, "cancelledAt cannot be null");
                Objects.requireNonNull(refundAmount, "refundAmount cannot be null");
                if (reason == null || reason.isBlank()) {
                    throw new DomainException("Cancellation reason cannot be blank");
                }
            }
        }
    }

    // ==================== ENTITIES & AGGREGATE ROOT ====================

    public static final class Reservation {
        private final ReservationId id;
        private final RoomId roomId;
        private final GuestId guestId;
        private final StayPeriod stayPeriod;
        private final Money totalPrice;
        private final CancellationPolicy cancellationPolicy;
        private ReservationState state;

        Reservation(
            ReservationId id,
            RoomId roomId,
            GuestId guestId,
            StayPeriod stayPeriod,
            Money totalPrice,
            CancellationPolicy cancellationPolicy,
            Instant requestedAt
        ) {
            this.id = Objects.requireNonNull(id, "ReservationId cannot be null");
            this.roomId = Objects.requireNonNull(roomId, "RoomId cannot be null");
            this.guestId = Objects.requireNonNull(guestId, "GuestId cannot be null");
            this.stayPeriod = Objects.requireNonNull(stayPeriod, "StayPeriod cannot be null");
            this.totalPrice = Objects.requireNonNull(totalPrice, "TotalPrice cannot be null");
            this.cancellationPolicy = Objects.requireNonNull(cancellationPolicy, "CancellationPolicy cannot be null");
            this.state = new ReservationState.Requested(requestedAt);
        }

        public void confirm(PaymentGuarantee guarantee, Instant confirmedAt) {
            if (state instanceof ReservationState.Requested) {
                this.state = new ReservationState.Confirmed(guarantee, confirmedAt);
            } else {
                throw new DomainException(
                    "Cannot confirm reservation " + id.value() + " from state " + state.getClass().getSimpleName()
                );
            }
        }

        public void checkIn(LocalDate arrivalDate, Instant checkedInAt) {
            if (state instanceof ReservationState.Confirmed confirmed) {
                if (arrivalDate.isBefore(stayPeriod.checkIn()) || !arrivalDate.isBefore(stayPeriod.checkOut())) {
                    throw new DomainException(
                        "Check-in date " + arrivalDate + " is outside the booked stay period " + stayPeriod
                    );
                }
                this.state = new ReservationState.CheckedIn(confirmed.guarantee(), checkedInAt);
            } else {
                throw new DomainException(
                    "Cannot check in reservation " + id.value() + " from state " + state.getClass().getSimpleName()
                );
            }
        }

        public void checkOut(Instant checkedOutAt) {
            if (state instanceof ReservationState.CheckedIn checkedIn) {
                this.state = new ReservationState.Completed(checkedIn.guarantee(), checkedIn.checkedInAt(), checkedOutAt);
            } else {
                throw new DomainException(
                    "Cannot check out reservation " + id.value() + " from state " + state.getClass().getSimpleName()
                );
            }
        }

        public void cancel(LocalDate cancellationDate, Instant cancelledAt, String reason) {
            if (state instanceof ReservationState.Requested) {
                this.state = new ReservationState.Cancelled(
                    cancelledAt,
                    Money.zero(totalPrice.currency()),
                    reason
                );
            } else if (state instanceof ReservationState.Confirmed) {
                Money refund = cancellationPolicy.calculateRefund(totalPrice, stayPeriod, cancellationDate);
                this.state = new ReservationState.Cancelled(cancelledAt, refund, reason);
            } else {
                throw new DomainException(
                    "Cancellation prohibited for reservation " + id.value() +
                    " in state " + state.getClass().getSimpleName()
                );
            }
        }

        public boolean occupiesRoom() {
            return !(state instanceof ReservationState.Cancelled);
        }

        public ReservationId id() { return id; }
        public RoomId roomId() { return roomId; }
        public GuestId guestId() { return guestId; }
        public StayPeriod stayPeriod() { return stayPeriod; }
        public Money totalPrice() { return totalPrice; }
        public ReservationState state() { return state; }
    }

    /**
     * Aggregate Root enforcing room availability and preventing overlapping reservations.
     */
    public static final class Room {
        private final RoomId roomId;
        private final Money nightlyRate;
        private final CancellationPolicy defaultPolicy;
        private final List<Reservation> reservations = new ArrayList<>();
        private long version = 0L; // Optimistic locking version for concurrency control

        public Room(RoomId roomId, Money nightlyRate, CancellationPolicy defaultPolicy) {
            this.roomId = Objects.requireNonNull(roomId, "RoomId cannot be null");
            this.nightlyRate = Objects.requireNonNull(nightlyRate, "Nightly rate cannot be null");
            this.defaultPolicy = Objects.requireNonNull(defaultPolicy, "Cancellation policy cannot be null");
        }

        public boolean isAvailableFor(StayPeriod requestedPeriod) {
            Objects.requireNonNull(requestedPeriod, "StayPeriod cannot be null");
            for (Reservation existing : reservations) {
                if (existing.occupiesRoom() && existing.stayPeriod().overlapsWith(requestedPeriod)) {
                    return false;
                }
            }
            return true;
        }

        public Reservation requestReservation(
            ReservationId reservationId,
            GuestId guestId,
            StayPeriod stayPeriod,
            Instant requestedAt
        ) {
            if (!isAvailableFor(stayPeriod)) {
                throw new DomainException(
                    "Room " + roomId.value() + " is already booked for overlapping dates: " + stayPeriod
                );
            }
            Money totalPrice = nightlyRate.multiply(stayPeriod.nights());
            Reservation reservation = new Reservation(
                reservationId,
                roomId,
                guestId,
                stayPeriod,
                totalPrice,
                defaultPolicy,
                requestedAt
            );
            reservations.add(reservation);
            version++;
            return reservation;
        }

        public RoomId roomId() { return roomId; }
        public long version() { return version; }
        public List<Reservation> reservations() { return Collections.unmodifiableList(reservations); }
    }

    // ==================== DEMONSTRATION ====================

    public static void main(String[] args) {
        Currency usd = Currency.getInstance("USD");
        CancellationPolicy policy = new CancellationPolicy(2, 50); // 100% refund >= 2 days before, 50% within 2 days
        Room room101 = new Room(new RoomId("RM-101"), Money.of("150.00", usd), policy);

        // 1. Create a valid reservation and progress through lifecycle
        StayPeriod stay1 = new StayPeriod(LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 15));
        Reservation res1 = room101.requestReservation(
            new ReservationId("RES-001"),
            new GuestId("GUEST-A"),
            stay1,
            Instant.now()
        );
        System.out.println("Created " + res1.id().value() + " for " + res1.stayPeriod() +
            " | Total: " + res1.totalPrice() + " | State: " + res1.state().getClass().getSimpleName());

        // Confirm with mandatory PaymentGuarantee
        res1.confirm(new PaymentGuarantee("AUTH-998877", Instant.now()), Instant.now());
        System.out.println("Confirmed " + res1.id().value() + " | State: " + res1.state().getClass().getSimpleName());

        // 2. Consecutive reservation on the exact check-out date (allowed)
        StayPeriod consecutiveStay = new StayPeriod(LocalDate.of(2026, 12, 15), LocalDate.of(2026, 12, 18));
        Reservation res2 = room101.requestReservation(
            new ReservationId("RES-002"),
            new GuestId("GUEST-B"),
            consecutiveStay,
            Instant.now()
        );
        res2.confirm(new PaymentGuarantee("AUTH-112233", Instant.now()), Instant.now());
        System.out.println("Consecutive booking " + res2.id().value() + " succeeded for " + res2.stayPeriod());

        // 3. Overlapping reservation attempt (rejected)
        try {
            StayPeriod overlappingStay = new StayPeriod(LocalDate.of(2026, 12, 12), LocalDate.of(2026, 12, 16));
            room101.requestReservation(new ReservationId("RES-003"), new GuestId("GUEST-C"), overlappingStay, Instant.now());
        } catch (DomainException e) {
            System.out.println("Blocked overlapping reservation: " + e.getMessage());
        }

        // 4. Guest checks in, then attempts cancellation (rejected)
        res1.checkIn(LocalDate.of(2026, 12, 10), Instant.now());
        System.out.println("Checked in " + res1.id().value() + " | State: " + res1.state().getClass().getSimpleName());
        try {
            res1.cancel(LocalDate.of(2026, 12, 11), Instant.now(), "Changed mind after check-in");
        } catch (DomainException e) {
            System.out.println("Blocked post-check-in cancellation: " + e.getMessage());
        }

        // Complete stay 1
        res1.checkOut(Instant.now());
        System.out.println("Checked out " + res1.id().value() + " | State: " + res1.state().getClass().getSimpleName());

        // 5. Cancel confirmed reservation before check-in (partial refund applied)
        res2.cancel(LocalDate.of(2026, 12, 14), Instant.now(), "Flight delayed");
        if (res2.state() instanceof ReservationState.Cancelled cancelled) {
            System.out.println("Cancelled " + res2.id().value() + " before check-in | Refund: " + cancelled.refundAmount());
        }
    }
}
