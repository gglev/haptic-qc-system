package qc.model;

import java.math.BigInteger;

public final class Measurement {
    private final BigInteger pointNo;
    private final double frequency;
    private final double acceleration;

    public Measurement(BigInteger pointNo, double frequency, double acceleration) {
        if (pointNo.signum() <= 0 || !Double.isFinite(frequency) || frequency <= 0
                || !Double.isFinite(acceleration) || acceleration < 0) {
            throw new IllegalArgumentException("Неверные значения точки измерения");
        }
        this.pointNo = pointNo;
        this.frequency = frequency;
        this.acceleration = acceleration;
    }

    public BigInteger getPointNo() { return pointNo; }
    public double getFrequency() { return frequency; }
    public double getAcceleration() { return acceleration; }
}
