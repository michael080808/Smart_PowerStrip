# -*- coding: utf-8 -*-
import time
import socket

host = '192.168.1.116'
port = 8080
commands = ['QM01', 'QM02', 'QF01', 'QF02', 'LT0000', 'LT0200', 'AL01', 'UL01', 'CL01', 'ST01']

if __name__ == '__main__':
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    s.settimeout(1)
    # 接收数据 自动阻塞 等待客户端请求:
    try:
        for c in commands:
            s.sendto(c.encode(encoding='utf-8'), (host, port))
            data, address = s.recvfrom(1024)
            print('Received from %s: %s\r\n%s' % (address[0], address[1], data.decode(encoding='utf-8')))
    except socket.timeout:
        print('Timeout')
    except KeyboardInterrupt:
        pass

# recvfrom()方法返回数据和客户端的地址与端口，这样，服务器收到数据后，直接调用sendto()就可以把数据用UDP发给客户端。
