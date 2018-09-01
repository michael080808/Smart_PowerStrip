# 智能插排

* * *

## 免责声明

本项目仅供本人学习交流使用，并不保证该项目的准确性和完整性。

对于其它环境使用该项目所出现的问题，本人概不负责。

同时本人没有长期维护、更新该项目的义务，仅为个人兴趣爱好。

## 设计概述

-   下位机设计

    采用NodeMCU-32S作为下位机。其核心是乐鑫（Espressif）生产的ESP32模组。

    ESP32是集成Wi-Fi、蓝牙及低功耗4.2的一款低成本SoC，具有广泛的应用场景。本项目使用的是其Wi-Fi功能进行设计

    与测量模块通信使用的是串口，与上位机通信使用UDP。采用分离操作，共享数据的方式。

    由于ESP32的SDK是基于FreeRTOS进行开发，所以很容易使用互斥锁和信号量对共用数据进行操作。

    配置方式：需要配置监控的路数NUM，蜂鸣器接口（低电平触发），用于控制通断的继电器接口（高电平触发），接收测量数据使用的串口（默认使用Serial2，RX：PORT 16，TX：PORT 17）

-   测量模块使用

    使用从淘宝购买的220V串口交流电能计量模块，使用串口进行通信获取数据。

    淘宝链接：<https://item.taobao.com/item.htm?id=568357190320>

    由于厂商的设计失误，厂商自行设计的简易通信协议无法正常使用，

    已经和厂商沟通（2018.8.31），厂商承诺尽快修复，因此本项目采用Modbus RTU获取测量数据

    Modbus RTU最大的难点在于其校验位的计算，下面给出具体方法。

    提供一个可用的校验网址：[链接地址](http://cht.nahua.com.tw/index.php?url=http://cht.nahua.com.tw/software/crc16/&key=Modbus,%20RTU,%20CRC16&title=%E8%A8%88%E7%AE%97%20Modbus%20RTU%20CRC16)

    提供一个可用的测试软件：[链接地址](http://yourplc.net/download/software/modbus_rtu/mbrtu.zip)

    -   Modbus RTU CRC16校验描述

          循环冗余校验(CRC) 域为两个字节，包含一个二进制16位值。附加在报文后面的CRC的值由发送设备计算。接收设备在接收报文时重新计算CRC的值，并将计算结果于实际接收到的CRC值相比较。如果两个值不相等，则为错误。

          CRC的计算, 开始对一个16位寄存器预装全1. 然后将报文中的连续的8位子节对其进行后续的计算。只有字符中的8个数据位参与生成CRC的运算，起始位，停止位和校验位不参与CRC计算。

          CRC的生成过程中， 每个 8–位字符与寄存器中的值异或。然后结果向最低有效位(LSB)方向移动(Shift) 1位，而最高有效位(MSB)位置充零。然后提取并检查LSB：如果LSB为1， 则寄存器中的值与一个固定的预置值异或；如果LSB为0， 则不进行异或操作。

          这个过程将重复直到执行完8次移位。完成最后一次（第8次）移位及相关操作后，下一个8位字节与寄存器的当前值异或，然后又同上面描述过的一样重复8次。当所有报文中子节都运算之后得到的寄存器中的最终值，就是CRC。

          生成CRC的过程为:

        1.  将一个16位寄存器装入十六进制FFFF(全1). 将之称作CRC寄存器.

        2.  将报文的第一个8位字节与16位CRC寄存器的低字节异或，结果置于CRC寄存器.

        3.  将CRC寄存器右移1位(向LSB方向)， MSB充零. 提取并检测LSB.

        4.  (如果LSB为0): 重复步骤3(另一次移位).

            (如果LSB为1): 对CRC寄存器异或多项式值0xA001(1010 0000 0000 0001).

        5.  重复步骤3和4，直到完成8次移位。当做完此操作后，将完成对8位字节的完整操作。

        6.  对报文中的下一个字节重复步骤2到5，继续此操作直至所有报文被处理完毕。

        7.  CRC寄存器中的最终内容为CRC值.

        8.  当放置CRC值于报文时，高低字节必须交换。

    -   指令描述（全部为十六进制数，数据全部采用MSB方式进行排列，从头到尾依次发送）

| 功能      | 发送指令格式                                        | 正确返回格式                                     | 错误返回格式         | 备注                              |
| ------- | --------------------------------------------- | ------------------------------------------ | -------------- | ------------------------------- |
| 读取单个寄存器 | SS 03 XX XX NN NN KK KK                       | SS 06 XX XX LL LL TT TT TT ... TT TT KK KK | SS 83 RR KK KK | TT TT TT ... TT TT 总共 MM MM 字节长 |
| 写入单个寄存器 | SS 06 XX XX TT TT KK KK                       | SS 06 XX XX TT TT KK KK                    | SS 86 RR KK KK |                                 |
| 写入多个寄存器 | SS 10 XX XX NN NN MM TT TT TT ... TT TT KK KK | SS 10 XX XX NN NN KK KK                    | SS 90 RR KK KK | TT TT TT ... TT TT 总共 MM MM 字节长 |

| 指令字段               | 字段长度（字节）   | 字段含义                  |
| ------------------ | ---------- | --------------------- |
| KK KK              | 2          | Modbus RTU CRC16 校验码  |
| LL LL              | 2          | 返回数据的字节长度             |
| MM                 | 1          | 写入数据的字节长度             |
| NN NN              | 2          | 写入寄存器个数               |
| RR                 | 1          | 错误码                   |
| SS                 | 1          | 设备地址码                 |
| TT TT              | 2          | 写入单个寄存器的数据            |
| TT TT TT ... TT TT | NN NN / MM | 读取NN NN个寄存器/写入MM字节的数据 |
| XX XX              | 2          | 寄存器地址                 |

-   Android程序描述

    使用Windows 10操作系统，Android Studio 3.1.4开发环境进行开发，设定Android SDK API版本最小23（Android 6.0 Marshmallow），最大27（Android 8.1 Oreo）

    通信全部采用UDP，采取发送指令，立即接收确认的方式，设定超时时间为1秒。图表使用Hello Charts进行开发。

    可以通过更改MainActivity.java中的final int NUMBER修改测量和控制用的路数
