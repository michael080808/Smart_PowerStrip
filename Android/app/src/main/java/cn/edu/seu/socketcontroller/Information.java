package cn.edu.seu.socketcontroller;

public class Information {
    private int alarm = 0;
    private int state = 0;
    private int alarmSet = 0;
    private int countDown = 0;
    private double voltage = 0;
    private double current = 0;
    private double power = 0;
    private double factor = 0;
    private double frequency = 0;
    private double consumption = 0;

    public void setAlarm(int i) {
        alarm = i;
    }

    public void setState(int i) {
        state = i;
    }

    public void setAlarmSet(int i) {
        alarmSet = i;
    }

    public void setCountDown(int i) {
        countDown = i;
    }

    public void setVoltage(long hex) {
        voltage = hex / 1000.0;
    }

    public void setVoltage(String hex) {
        voltage = Integer.valueOf(hex, 16) / 1000.0;
    }

    public void setCurrent(long hex) {
        current = hex / 1000.0;
    }

    public void setCurrent(String hex) {
        current = Integer.valueOf(hex, 16) / 1000.0;
    }

    public void setPower(long hex) {
        power = hex / 1000.0;
    }

    public void setPower(String hex) {
        power = Integer.valueOf(hex, 16) / 1000.0;
    }

    public void setFactor(long hex) {
        factor = hex / 10000.0;
    }

    public void setFactor(String hex) {
        factor = Integer.valueOf(hex, 16) / 10000.0;
    }

    public void setFrequency(long hex) {
        frequency = hex / 1000.0;
    }

    public void setFrequency(String hex) {
        frequency = Integer.valueOf(hex, 16) / 1000.0;
    }

    public void setConsumption(long hex) {
        consumption = hex / 10000.0;
    }

    public void setConsumption(String hex) {
        consumption = Integer.valueOf(hex, 16) / 10000.0;
    }

    public int getAlarm() {
        return alarm;
    }

    public int getState() {
        return state;
    }

    public int getAlarmSet() {
        return alarmSet;
    }

    public int getCountDown() {
        return countDown;
    }

    public double getVoltage() {
        return voltage;
    }

    public String getVoltageString() {
        return Double.toString(voltage) + "V";
    }

    public double getCurrent() {
        return current;
    }

    public String getCurrentString() {
        return Double.toString(current) + "A";
    }

    public double getPower() {
        return power;
    }

    public String getPowerString() {
        return Double.toString(power) + "W";
    }

    public double getFactor() {
        return factor;
    }

    public String getFactorString() {
        return Double.toString(factor);
    }

    public double getFrequency() {
        return frequency;
    }

    public String getFrequencyString() {
        return Double.toString(frequency) + "Hz";
    }

    public double getConsumption() {
        return consumption;
    }

    public String getConsumptionString() {
        return Double.toString(consumption) + "kW·h";
    }
}
