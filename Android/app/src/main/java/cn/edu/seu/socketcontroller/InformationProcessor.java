package cn.edu.seu.socketcontroller;

public class InformationProcessor {
    public static Information InformationProcess(String response, Command command) {
        if (command == Command.Query_Main) {
            if (response.length() == 37) {
                Information info = new Information();
                if (response.charAt(0) == 'U')
                    info.setVoltage(response.substring(1, 9));
                else
                    throw new NumberFormatException();

                if (response.charAt(9) == 'I')
                    info.setCurrent(response.substring(10, 18));
                else
                    throw new NumberFormatException();

                if (response.charAt(18) == 'P')
                    info.setPower(response.substring(19, 27));
                else
                    throw new NumberFormatException();

                if (response.charAt(27) == 'A')
                    info.setAlarm(response.charAt(28) - '0');
                else
                    throw new NumberFormatException();

                if (response.charAt(29) == 'T')
                    info.setAlarmSet(response.charAt(30) - '0');
                else
                    throw new NumberFormatException();

                if (response.charAt(31) == 'S')
                    info.setState(response.charAt(32) - '0');
                else
                    throw new NumberFormatException();

                if (response.charAt(33) == 'C')
                    info.setCountDown(Integer.valueOf(response.substring(34, 35)));
                else
                    throw new NumberFormatException();

                return info;
            } else {
                throw new NumberFormatException();
            }
        } else if (command == Command.Query_Full) {
            if (response.length() == 64) {
                Information info = new Information();
                if (response.charAt(0) == 'U')
                    info.setVoltage(response.substring(1, 9));
                else
                    throw new NumberFormatException();

                if (response.charAt(9) == 'I')
                    info.setCurrent(response.substring(10, 18));
                else
                    throw new NumberFormatException();

                if (response.charAt(18) == 'P')
                    info.setPower(response.substring(19, 27));
                else
                    throw new NumberFormatException();

                if (response.charAt(27) == 'F')
                    info.setFactor(response.substring(28, 36));
                else
                    throw new NumberFormatException();

                if (response.charAt(36) == 'f')
                    info.setFrequency(response.substring(37, 45));
                else
                    throw new NumberFormatException();

                if (response.charAt(45) == 'W')
                    info.setConsumption(response.substring(46, 54));
                else
                    throw new NumberFormatException();

                if (response.charAt(54) == 'A')
                    info.setAlarm(response.charAt(55) - '0');
                else
                    throw new NumberFormatException();

                if (response.charAt(56) == 'T')
                    info.setAlarmSet(response.charAt(57) - '0');
                else
                    throw new NumberFormatException();

                if (response.charAt(58) == 'S')
                    info.setState(response.charAt(59) - '0');
                else
                    throw new NumberFormatException();

                if (response.charAt(60) == 'C')
                    info.setCountDown(Integer.valueOf(response.substring(61, 62)));
                else
                    throw new NumberFormatException();

                return info;
            } else {
                throw new NumberFormatException();
            }
        } else {
            throw new IllegalArgumentException();
        }
    }

    public static boolean InformationVerify(String response, Command command) {
        if (command == Command.Limit || command == Command.Alarm || command == Command.Relieve || command == Command.On || command == Command.Off) {
            return response.equals("OK");
        } else {
            throw new IllegalArgumentException();
        }
    }
}
