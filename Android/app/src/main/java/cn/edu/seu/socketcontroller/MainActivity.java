package cn.edu.seu.socketcontroller;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import lecho.lib.hellocharts.gesture.ContainerScrollType;
import lecho.lib.hellocharts.model.Axis;
import lecho.lib.hellocharts.model.AxisValue;
import lecho.lib.hellocharts.model.Line;
import lecho.lib.hellocharts.model.LineChartData;
import lecho.lib.hellocharts.model.PointValue;
import lecho.lib.hellocharts.model.Viewport;
import lecho.lib.hellocharts.view.LineChartView;

import static java.lang.Math.random;

public class MainActivity extends AppCompatActivity {
    final static String[] permissions = {Manifest.permission.INTERNET};
    final static int PERMISSION_REQUEST_ON_CREATE = 0x05220112;
    final static int PERMISSION_REQUEST_ON_CONNECT = 0x05220127;

    final static int NUMBER = 2;
    int ADDRESS_INDEX = 0;

    int ADDRESS_DEVICE_INDEX = 0;
    Lock deviceChangeLock = new ReentrantLock();

    GraphicCharts chartsType = GraphicCharts.Power;
    Lock chartTypeLock = new ReentrantLock();

    ExecutorService temporaryService;
    ScheduledExecutorService scheduledService;

    int[] count_down_prev = new int[NUMBER];
    int[] count_down_next = new int[NUMBER];
    int[] alarm_flag_prev = new int[NUMBER];
    int[] alarm_flag_next = new int[NUMBER];
    int[] state_flag_next = new int[NUMBER];
    int[] alarm_set_prev  = new int[NUMBER];

    int lineChartPointIndex = 0;
    List<AxisValue> lineChartAxisXValues = new ArrayList<>();

    List<PointValue>[] lineChartVoltagePointValues = new List[NUMBER];
    List<PointValue>[] lineChartCurrentPointValues = new List[NUMBER];
    List<PointValue>[] lineChartPowerPointValues = new List[NUMBER];
    List<PointValue>[] lineChartFactorPointValues = new List[NUMBER];
    List<PointValue>[] lineChartFrequencyPointValues = new List[NUMBER];
    List<PointValue>[] lineChartConsumptionPointValues = new List[NUMBER];

    List<PointValue> lineChartCurrentSumPointValues = new ArrayList<>();
    List<PointValue> lineChartPowerSumPointValues = new ArrayList<>();
    List<PointValue> lineChartConsumptionSumPointValues = new ArrayList<>();

    int[][] colors = new int[NUMBER][6];
    int[] specialColors = new int[3];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        for (int i = 0; i < NUMBER; i++)
            for (int j = 0; j < 6; j++) {
                colors[i][j] = Color.rgb((int) (random() * 255), (int) (random() * 255), (int) (random() * 255));
            }

        for (int i = 0; i < 3; i++)
            specialColors[i] = Color.rgb((int) (random() * 255), (int) (random() * 255), (int) (random() * 255));

        temporaryService = Executors.newSingleThreadExecutor();
        scheduledService = Executors.newScheduledThreadPool(2);

        for (String permission : permissions)
            if (checkSelfPermission(permission) == PackageManager.PERMISSION_DENIED) {
                requestPermissions(permissions, PERMISSION_REQUEST_ON_CREATE);
                break;
            }

        for (int i = 0; i < NUMBER; i++) {
            lineChartVoltagePointValues[i] = new ArrayList<>();
            lineChartCurrentPointValues[i] = new ArrayList<>();
            lineChartPowerPointValues[i] = new ArrayList<>();
            lineChartFactorPointValues[i] = new ArrayList<>();
            lineChartFrequencyPointValues[i] = new ArrayList<>();
            lineChartConsumptionPointValues[i] = new ArrayList<>();
        }

        final TextView status = findViewById(R.id.status);

        final EditText hostEditText = findViewById(R.id.host);
        final EditText portEditText = findViewById(R.id.port);

        final TextView titleTextView = findViewById(R.id.title);
        final TextView voltageTextView = findViewById(R.id.voltage);
        final TextView currentTextView = findViewById(R.id.current);
        final TextView powerTextView = findViewById(R.id.power);
        final TextView factorTextView = findViewById(R.id.factor);
        final TextView frequencyTextView = findViewById(R.id.frequency);
        final TextView consumptionTextView = findViewById(R.id.consumption);

        final LineChartView lineChart = findViewById(R.id.lineChart);
        final Button alarmButton = findViewById(R.id.alarm);
        final Button switchButton = findViewById(R.id.deviceSwitch);
        final Spinner deviceSpinner = findViewById(R.id.devices);
        List<String> spinnerItems = new ArrayList<>();
        for (int i = 0; i < NUMBER; i++)
            spinnerItems.add("Device " + Integer.toString(i + 1));
        deviceSpinner.setAdapter(new ArrayAdapter<>(this, R.layout.support_simple_spinner_dropdown_item, spinnerItems));

        scheduledService.scheduleAtFixedRate(() -> {
            deviceChangeLock.lock();
            ADDRESS_DEVICE_INDEX = (ADDRESS_DEVICE_INDEX + 1) % (NUMBER + 1);
            deviceChangeLock.unlock();
        }, 0, 5, TimeUnit.SECONDS);

        scheduledService.scheduleAtFixedRate(() -> {
            if (hostEditText.getText().length() > 0 && portEditText.getText().length() > 0) {
                try {
                    DatagramSocket socket;

                    socket = new DatagramSocket(0);
                    Log.i("Datagram Packet", "Try to Send UDP Command...");
                    socket.setSoTimeout(1000);
                    String send = Command.Query_Full.Command(ADDRESS_INDEX + 1);
                    byte receive_bytes[] = new byte[100];
                    DatagramPacket request = new DatagramPacket(send.getBytes(), send.getBytes().length, InetAddress.getByName(hostEditText.getText().toString()), Integer.valueOf(portEditText.getText().toString()));
                    DatagramPacket receive = new DatagramPacket(receive_bytes, receive_bytes.length);
                    socket.send(request);
                    Log.i("Datagram Packet", String.format("Send to %s:%d, Length: %d, Data: %s", InetAddress.getByName(hostEditText.getText().toString()), Integer.valueOf(portEditText.getText().toString()), send.length(), send));
                    socket.receive(receive);
                    Log.i("Datagram Packet", String.format("Receive From %s:%d, Length: %d, Data: %s", receive.getAddress(), receive.getPort(), receive.getLength(), new String(receive.getData()).substring(0, receive.getLength())));
                    socket.close();

                    Information informationElement = InformationProcessor.InformationProcess(new String(receive.getData()).substring(0, receive.getLength()), Command.Query_Full);

                    alarm_flag_prev[ADDRESS_INDEX] = alarm_flag_next[ADDRESS_INDEX];
                    alarm_flag_next[ADDRESS_INDEX] = informationElement.getAlarm();
                    count_down_prev[ADDRESS_INDEX] = count_down_next[ADDRESS_INDEX];
                    count_down_next[ADDRESS_INDEX] = informationElement.getCountDown();
                    state_flag_next[ADDRESS_INDEX] = informationElement.getState();
                    alarm_set_prev [ADDRESS_INDEX] = informationElement.getAlarmSet();

                    if (ADDRESS_INDEX == 0) {
                        lineChartAxisXValues.add(new AxisValue(lineChartPointIndex).setLabel(lineChartPointIndex + ""));
                        if (lineChartAxisXValues.size() > 121)
                            lineChartAxisXValues.remove(0);
                    }

                    lineChartVoltagePointValues[ADDRESS_INDEX].add(new PointValue(lineChartPointIndex, (float) informationElement.getVoltage()));
                    if (lineChartVoltagePointValues[ADDRESS_INDEX].size() > 121)
                        lineChartVoltagePointValues[ADDRESS_INDEX].remove(0);
                    lineChartCurrentPointValues[ADDRESS_INDEX].add(new PointValue(lineChartPointIndex, (float) informationElement.getCurrent()));
                    if (lineChartCurrentPointValues[ADDRESS_INDEX].size() > 121)
                        lineChartCurrentPointValues[ADDRESS_INDEX].remove(0);
                    lineChartPowerPointValues[ADDRESS_INDEX].add(new PointValue(lineChartPointIndex, (float) informationElement.getPower()));
                    if (lineChartPowerPointValues[ADDRESS_INDEX].size() > 121)
                        lineChartPowerPointValues[ADDRESS_INDEX].remove(0);
                    lineChartFactorPointValues[ADDRESS_INDEX].add(new PointValue(lineChartPointIndex, (float) informationElement.getFactor()));
                    if (lineChartFactorPointValues[ADDRESS_INDEX].size() > 121)
                        lineChartFactorPointValues[ADDRESS_INDEX].remove(0);
                    lineChartFrequencyPointValues[ADDRESS_INDEX].add(new PointValue(lineChartPointIndex, (float) informationElement.getFrequency()));
                    if (lineChartFrequencyPointValues[ADDRESS_INDEX].size() > 121)
                        lineChartFrequencyPointValues[ADDRESS_INDEX].remove(0);
                    lineChartConsumptionPointValues[ADDRESS_INDEX].add(new PointValue(lineChartPointIndex, (float) informationElement.getConsumption()));
                    if (lineChartConsumptionPointValues[ADDRESS_INDEX].size() > 121)
                        lineChartConsumptionPointValues[ADDRESS_INDEX].remove(0);

                    if (ADDRESS_INDEX + 1 >= NUMBER) {
                        float currentSum = 0f, powerSum = 0f, consumptionSum = 0;
                        for (int i = 0; i < NUMBER; i++) {
                            currentSum += (lineChartCurrentPointValues[i].get(lineChartVoltagePointValues[i].size() - 1)).getY();
                            powerSum += (lineChartPowerPointValues[i].get(lineChartVoltagePointValues[i].size() - 1)).getY();
                            consumptionSum += (lineChartConsumptionPointValues[i].get(lineChartVoltagePointValues[i].size() - 1)).getY();
                        }
                        lineChartCurrentSumPointValues.add(new PointValue(lineChartPointIndex, currentSum));
                        if (lineChartCurrentSumPointValues.size() > 121)
                            lineChartCurrentSumPointValues.remove(0);
                        lineChartPowerSumPointValues.add(new PointValue(lineChartPointIndex, powerSum));
                        if (lineChartPowerSumPointValues.size() > 121)
                            lineChartPowerSumPointValues.remove(0);
                        lineChartConsumptionSumPointValues.add(new PointValue(lineChartPointIndex, consumptionSum));
                        if (lineChartConsumptionSumPointValues.size() > 121)
                            lineChartConsumptionSumPointValues.remove(0);
                    }

                    runOnUiThread(() -> {
                        deviceChangeLock.lock();
                        int temporaryAddressIndex = ADDRESS_DEVICE_INDEX;
                        deviceChangeLock.unlock();

                        if(temporaryAddressIndex < NUMBER) {
                            titleTextView.setText(String.format("Device %d", temporaryAddressIndex + 1));
                            voltageTextView.setText(String.format("%.3fV", lineChartVoltagePointValues[temporaryAddressIndex].get(lineChartVoltagePointValues[temporaryAddressIndex].size() - 1).getY()));
                            currentTextView.setText(String.format("%.3fA", lineChartCurrentPointValues[temporaryAddressIndex].get(lineChartCurrentPointValues[temporaryAddressIndex].size() - 1).getY()));
                            powerTextView.setText(String.format("%.3fW", lineChartPowerPointValues[temporaryAddressIndex].get(lineChartPowerPointValues[temporaryAddressIndex].size() - 1).getY()));
                            factorTextView.setText(String.format("%.4f", lineChartFactorPointValues[temporaryAddressIndex].get(lineChartFactorPointValues[temporaryAddressIndex].size() - 1).getY()));
                            frequencyTextView.setText(String.format("%.3fHz", lineChartFrequencyPointValues[temporaryAddressIndex].get(lineChartFrequencyPointValues[temporaryAddressIndex].size() - 1).getY()));
                            consumptionTextView.setText(String.format("%.4fkW·h", lineChartConsumptionPointValues[temporaryAddressIndex].get(lineChartConsumptionPointValues[temporaryAddressIndex].size() - 1).getY()));
                        } else {
                            titleTextView.setText("Collection");
                            voltageTextView.setText("Voltage");
                            currentTextView.setText(String.format("%.3fA", lineChartCurrentSumPointValues.get(lineChartCurrentSumPointValues.size() - 1).getY()));
                            powerTextView.setText(String.format("%.3fW", lineChartPowerSumPointValues.get(lineChartPowerSumPointValues.size() - 1).getY()));
                            factorTextView.setText("Factor");
                            frequencyTextView.setText("Frequency");
                            consumptionTextView.setText(String.format("%.4fkW·h", lineChartConsumptionSumPointValues.get(lineChartConsumptionSumPointValues.size() - 1).getY()));
                        }
                        status.setTextColor(Color.parseColor("#00FF00"));
                        status.setText("Query Successfully!");

                        GraphicCharts temporaryChartsType;
                        chartTypeLock.lock();
                        temporaryChartsType = chartsType;
                        chartTypeLock.unlock();

                        if (ADDRESS_INDEX + 1 >= NUMBER) {
                            List<Line> lines = new ArrayList<>();
                            for (int i = 0; i < NUMBER; i++) {
                                Line line = new Line();
                                switch (temporaryChartsType) {
                                    case Voltage:
                                        line = new Line(lineChartVoltagePointValues[i]);
                                        line.setColor(colors[i][0]);
                                        break;
                                    case Current:
                                        line = new Line(lineChartCurrentPointValues[i]);
                                        line.setColor(colors[i][1]);
                                        break;
                                    case Power:
                                        line = new Line(lineChartPowerPointValues[i]);
                                        line.setColor(colors[i][2]);
                                        break;
                                    case Factor:
                                        line = new Line(lineChartFactorPointValues[i]);
                                        line.setColor(colors[i][3]);
                                        break;
                                    case Frequency:
                                        line = new Line(lineChartFrequencyPointValues[i]);
                                        line.setColor(colors[i][4]);
                                        break;
                                    case Consumption:
                                        line = new Line(lineChartConsumptionPointValues[i]);
                                        line.setColor(colors[i][5]);
                                        break;
                                }
                                line.setCubic(true);
                                line.setHasLines(true);
                                line.setHasPoints(false);
                                lines.add(line);
                            }

                            Line line = null;
                            if (temporaryChartsType == GraphicCharts.Current) {
                                line = new Line(lineChartCurrentSumPointValues);
                                line.setColor(specialColors[0]);
                            }
                            if (temporaryChartsType == GraphicCharts.Power) {
                                line = new Line(lineChartPowerSumPointValues);
                                line.setColor(specialColors[1]);
                            }
                            if (temporaryChartsType == GraphicCharts.Consumption) {
                                line = new Line(lineChartConsumptionSumPointValues);
                                line.setColor(specialColors[2]);
                            }
                            if (line != null) {
                                line.setCubic(true);
                                line.setHasLines(true);
                                line.setHasPoints(false);
                                lines.add(line);
                            }

                            LineChartData data = new LineChartData();
                            data.setLines(lines);

                            Axis axisX = new Axis();
                            axisX.setValues(lineChartAxisXValues);
                            axisX.setName("Time(s)");
                            data.setAxisXBottom(axisX);
                            Axis axisY = new Axis();
                            switch (temporaryChartsType) {
                                case Voltage:
                                    axisY.setName("Voltage(V)");
                                    break;
                                case Current:
                                    axisY.setName("Current(A)");
                                    break;
                                case Power:
                                    axisY.setName("Power(W)");
                                    break;
                                case Factor:
                                    axisY.setName("Factor");
                                    break;
                                case Frequency:
                                    axisY.setName("Frequency(Hz)");
                                    break;
                                case Consumption:
                                    axisY.setName("Consumption(kW·h)");
                                    break;
                            }
                            data.setAxisYLeft(axisY);

                            lineChart.setInteractive(false);
                            lineChart.setContainerScrollEnabled(true, ContainerScrollType.HORIZONTAL);
                            lineChart.setLineChartData(data);
                            lineChart.setVisibility(View.VISIBLE);

                            Viewport viewport = new Viewport();

                            if (lineChartPointIndex > 120) {
                                viewport.left = lineChartPointIndex - 120;
                                viewport.right = lineChartPointIndex;
                            } else {
                                viewport.left = 0;
                                viewport.right = 120;
                            }

                            viewport.top = (float) (lineChart.getMaximumViewport().top + 0.2 * (lineChart.getMaximumViewport().top - lineChart.getMaximumViewport().bottom));
                            viewport.bottom = (float) (lineChart.getMaximumViewport().bottom - 0.2 * (lineChart.getMaximumViewport().top - lineChart.getMaximumViewport().bottom));
                            lineChart.setMaximumViewport(viewport);
                            lineChart.setCurrentViewport(viewport);
                        }

                        if(deviceSpinner.getSelectedItemPosition() == ADDRESS_INDEX) {
                            if(alarm_set_prev[ADDRESS_INDEX] == 0)
                                switchButton.setText("Alarm");
                            else
                                switchButton.setText("Relieve");

                            if(state_flag_next[ADDRESS_INDEX] == 1)
                                switchButton.setText("OFF");
                            else
                                switchButton.setText("ON");
                        }

                        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);

                        if (count_down_prev[ADDRESS_INDEX] == 0 && count_down_next[ADDRESS_INDEX] > 0) {
                            new AlertDialog.Builder(this).setTitle("Power Limit Exceeded").setMessage(String.format("You meet the Power Limit.\r\nDevice %d has shutdown for 10 seconds.", ADDRESS_INDEX + 1)).setPositiveButton("OK", null).show();
                            vibrator.vibrate(1000);
                        }

                        if (count_down_next[ADDRESS_INDEX] > 0) {
                            status.setTextColor(Color.parseColor("#FF0000"));
                            status.setText(String.format("Device %d Power Limit Exceeded!", ADDRESS_INDEX + 1));
                        }

                        if (alarm_flag_prev[ADDRESS_INDEX] == 0 && alarm_flag_next[ADDRESS_INDEX] == 1) {
                            new AlertDialog.Builder(this).setTitle("Use Alarm").setMessage(String.format("Somebody is using Device %d.", ADDRESS_INDEX + 1)).setPositiveButton("OK", null).show();
                            vibrator.vibrate(1000);
                        }

                        if (alarm_flag_next[ADDRESS_INDEX] == 1) {
                            status.setTextColor(Color.parseColor("#FF0000"));
                            status.setText(String.format("Device %d Use Alarm!", ADDRESS_INDEX + 1));
                        }
                    });

                    if (ADDRESS_INDEX + 1 >= NUMBER)
                        lineChartPointIndex++;
                    ADDRESS_INDEX = (ADDRESS_INDEX + 1) % NUMBER;
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        status.setTextColor(Color.parseColor("#FFB90F"));
                        status.setText(e.getMessage());
                    });
                    Log.e("Exception", e.getMessage());
                }
            }
        }, 0, 1000 / NUMBER, TimeUnit.MILLISECONDS);

        final Button incButton = findViewById(R.id.inc);
        final Button decButton = findViewById(R.id.dec);
        final Button sendButton = findViewById(R.id.send);
        final EditText limitEditText = findViewById(R.id.limit);

        sendButton.setOnClickListener(v -> {
            temporaryService.submit(() -> {
                try {
                    DatagramSocket socket = new DatagramSocket(0);
                    Log.i("Datagram Packet", "Try to Send UDP Command...");
                    socket.setSoTimeout(1000);
                    String send = Command.Limit.Command(Integer.valueOf(limitEditText.getText().toString()));
                    byte receive_bytes[] = new byte[100];
                    DatagramPacket request = new DatagramPacket(send.getBytes(), send.getBytes().length, InetAddress.getByName(hostEditText.getText().toString()), Integer.valueOf(portEditText.getText().toString()));
                    DatagramPacket receive = new DatagramPacket(receive_bytes, receive_bytes.length);
                    socket.send(request);
                    Log.i("Datagram Packet", String.format("Send to %s:%d, Length: %d, Data: %s", InetAddress.getByName(hostEditText.getText().toString()), Integer.valueOf(portEditText.getText().toString()), send.length(), send));
                    socket.receive(receive);
                    Log.i("Datagram Packet", String.format("Receive From %s:%d, Length: %d, Data: %s", receive.getAddress(), receive.getPort(), receive.getLength(), new String(receive.getData()).substring(0, receive.getLength())));
                    socket.close();

                    String receive_str = new String(receive.getData()).substring(0, receive.getLength());
                    runOnUiThread(() -> {
                        if (receive_str.equals("OK\r\n")) {
                            status.setTextColor(Color.parseColor("#00FF00"));
                            status.setText("Set Limit Successfully!");
                        } else {
                            status.setTextColor(Color.parseColor("#FF0000"));
                            status.setText("Set Limit Failure!");
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        status.setTextColor(Color.parseColor("#FFB90F"));
                        status.setText(e.getMessage());
                    });
                    Log.e("Exception", e.getMessage());
                }
            });
        });
        incButton.setOnClickListener(v -> {
            limitEditText.setText(Integer.toString(Integer.valueOf(limitEditText.getText().toString()) + 1));
        });
        decButton.setOnClickListener(v -> {
            limitEditText.setText(Integer.toString(Integer.valueOf(limitEditText.getText().toString()) - 1));
        });

        alarmButton.setOnClickListener(v -> {
            if (alarmButton.getText().equals("Alarm")) {
                temporaryService.submit(() -> {
                    try {
                        DatagramSocket socket = new DatagramSocket(0);
                        Log.i("Datagram Packet", "Try to Send UDP Command...");
                        socket.setSoTimeout(1000);
                        String send = Command.Alarm.Command(deviceSpinner.getSelectedItemPosition() + 1);
                        byte receive_bytes[] = new byte[100];
                        DatagramPacket request = new DatagramPacket(send.getBytes(), send.getBytes().length, InetAddress.getByName(hostEditText.getText().toString()), Integer.valueOf(portEditText.getText().toString()));
                        DatagramPacket receive = new DatagramPacket(receive_bytes, receive_bytes.length);
                        socket.send(request);
                        Log.i("Datagram Packet", String.format("Send to %s:%d, Length: %d, Data: %s", InetAddress.getByName(hostEditText.getText().toString()), Integer.valueOf(portEditText.getText().toString()), send.length(), send));
                        socket.receive(receive);
                        Log.i("Datagram Packet", String.format("Receive From %s:%d, Length: %d, Data: %s", receive.getAddress(), receive.getPort(), receive.getLength(), new String(receive.getData()).substring(0, receive.getLength())));
                        socket.close();

                        String receive_str = new String(receive.getData()).substring(0, receive.getLength());
                        runOnUiThread(() -> {
                            if (receive_str.equals("OK\r\n")) {
                                alarmButton.setText("Relieve");
                                status.setTextColor(Color.parseColor("#00FF00"));
                                status.setText("Alarm Successfully!");
                            } else {
                                status.setTextColor(Color.parseColor("#FF0000"));
                                status.setText("Alarm Failure!");
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            status.setTextColor(Color.parseColor("#FFB90F"));
                            status.setText(e.getMessage());
                        });
                        Log.e("Exception", e.getMessage());
                    }
                });
            } else {
                temporaryService.submit(() -> {
                    try {
                        DatagramSocket socket = new DatagramSocket(0);
                        Log.i("Datagram Packet", "Try to Send UDP Command...");
                        socket.setSoTimeout(1000);
                        String send = Command.Relieve.Command(deviceSpinner.getSelectedItemPosition() + 1);
                        byte receive_bytes[] = new byte[100];
                        DatagramPacket request = new DatagramPacket(send.getBytes(), send.getBytes().length, InetAddress.getByName(hostEditText.getText().toString()), Integer.valueOf(portEditText.getText().toString()));
                        DatagramPacket receive = new DatagramPacket(receive_bytes, receive_bytes.length);
                        socket.send(request);
                        Log.i("Datagram Packet", String.format("Send to %s:%d, Length: %d, Data: %s", InetAddress.getByName(hostEditText.getText().toString()), Integer.valueOf(portEditText.getText().toString()), send.length(), send));
                        socket.receive(receive);
                        Log.i("Datagram Packet", String.format("Receive From %s:%d, Length: %d, Data: %s", receive.getAddress(), receive.getPort(), receive.getLength(), new String(receive.getData()).substring(0, receive.getLength())));
                        socket.close();

                        String receive_str = new String(receive.getData()).substring(0, receive.getLength());
                        runOnUiThread(() -> {
                            if (receive_str.equals("OK\r\n")) {
                                alarmButton.setText("Alarm");
                                status.setTextColor(Color.parseColor("#00FF00"));
                                status.setText("Relieve Successfully!");
                            } else {
                                status.setTextColor(Color.parseColor("#FF0000"));
                                status.setText("Relieve Failure!");
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            status.setTextColor(Color.parseColor("#FFB90F"));
                            status.setText(e.getMessage());
                        });
                        Log.e("Exception", e.getMessage());
                    }
                });
            }
        });
        switchButton.setOnClickListener(v -> {
            if (switchButton.getText().equals("ON")) {
                temporaryService.submit(() -> {
                    try {
                        DatagramSocket socket = new DatagramSocket(0);
                        Log.i("Datagram Packet", "Try to Send UDP Command...");
                        socket.setSoTimeout(1000);
                        String send = Command.On.Command(deviceSpinner.getSelectedItemPosition() + 1);
                        byte receive_bytes[] = new byte[100];
                        DatagramPacket request = new DatagramPacket(send.getBytes(), send.getBytes().length, InetAddress.getByName(hostEditText.getText().toString()), Integer.valueOf(portEditText.getText().toString()));
                        DatagramPacket receive = new DatagramPacket(receive_bytes, receive_bytes.length);
                        socket.send(request);
                        Log.i("Datagram Packet", String.format("Send to %s:%d, Length: %d, Data: %s", InetAddress.getByName(hostEditText.getText().toString()), Integer.valueOf(portEditText.getText().toString()), send.length(), send));
                        socket.receive(receive);
                        Log.i("Datagram Packet", String.format("Receive From %s:%d, Length: %d, Data: %s", receive.getAddress(), receive.getPort(), receive.getLength(), new String(receive.getData()).substring(0, receive.getLength())));
                        socket.close();

                        String receive_str = new String(receive.getData()).substring(0, receive.getLength());
                        runOnUiThread(() -> {
                            if (receive_str.equals("OK\r\n")) {
                                switchButton.setText("OFF");
                                status.setTextColor(Color.parseColor("#00FF00"));
                                status.setText("Turn ON Successfully!");
                            } else {
                                status.setTextColor(Color.parseColor("#FF0000"));
                                status.setText("Turn ON Failure!");
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            status.setTextColor(Color.parseColor("#FFB90F"));
                            status.setText(e.getMessage());
                        });
                        Log.e("Exception", e.getMessage());
                    }
                });
            } else {
                temporaryService.submit(() -> {
                    try {
                        DatagramSocket socket = new DatagramSocket(0);
                        Log.i("Datagram Packet", "Try to Send UDP Command...");
                        socket.setSoTimeout(1000);
                        String send = Command.Off.Command(deviceSpinner.getSelectedItemPosition() + 1);
                        byte receive_bytes[] = new byte[100];
                        DatagramPacket request = new DatagramPacket(send.getBytes(), send.getBytes().length, InetAddress.getByName(hostEditText.getText().toString()), Integer.valueOf(portEditText.getText().toString()));
                        DatagramPacket receive = new DatagramPacket(receive_bytes, receive_bytes.length);
                        socket.send(request);
                        Log.i("Datagram Packet", String.format("Send to %s:%d, Length: %d, Data: %s", InetAddress.getByName(hostEditText.getText().toString()), Integer.valueOf(portEditText.getText().toString()), send.length(), send));
                        socket.receive(receive);
                        Log.i("Datagram Packet", String.format("Receive From %s:%d, Length: %d, Data: %s", receive.getAddress(), receive.getPort(), receive.getLength(), new String(receive.getData()).substring(0, receive.getLength())));
                        socket.close();

                        String receive_str = new String(receive.getData()).substring(0, receive.getLength());
                        runOnUiThread(() -> {
                            if (receive_str.equals("OK\r\n")) {
                                switchButton.setText("ON");
                                status.setTextColor(Color.parseColor("#00FF00"));
                                status.setText("Turn OFF Successfully!");
                            } else {
                                status.setTextColor(Color.parseColor("#FF0000"));
                                status.setText("Turn OFF Failure!");
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            status.setTextColor(Color.parseColor("#FFB90F"));
                            status.setText(e.getMessage());
                        });
                        Log.e("Exception", e.getMessage());
                    }
                });
            }
        });

        voltageTextView.setOnClickListener(v -> {
            chartTypeLock.lock();
            chartsType = GraphicCharts.Voltage;
            chartTypeLock.unlock();
        });
        currentTextView.setOnClickListener(v -> {
            chartTypeLock.lock();
            chartsType = GraphicCharts.Current;
            chartTypeLock.unlock();
        });
        powerTextView.setOnClickListener(v -> {
            chartTypeLock.lock();
            chartsType = GraphicCharts.Power;
            chartTypeLock.unlock();
        });
        factorTextView.setOnClickListener(v -> {
            chartTypeLock.lock();
            chartsType = GraphicCharts.Factor;
            chartTypeLock.unlock();
        });
        frequencyTextView.setOnClickListener(v -> {
            chartTypeLock.lock();
            chartsType = GraphicCharts.Frequency;
            chartTypeLock.unlock();
        });
        consumptionTextView.setOnClickListener(v -> {
            chartTypeLock.lock();
            chartsType = GraphicCharts.Consumption;
            chartTypeLock.unlock();
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_ON_CONNECT) {
            for (int grantResult : grantResults) {
                if (grantResult == PackageManager.PERMISSION_DENIED)
                    return;
            }
        }
    }
}
