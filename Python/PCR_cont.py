#!/usr/bin/env python
# PCR controller base functions
# Jaeduk Han (05/19/2016)

import time
import serial
import numpy as np
import matplotlib.pyplot as plt
import sys
import glob
from IPython import display
from ipywidgets import widgets
from collections import deque
flag_sim=0;
temp_sim=30;
led_sim=0;
fan_sim=0;
ser_global=0;
def encNum(value): #encode numbers for serial comm
    if value<10:
        cmdstr='0'+'0'+str(value)
    elif value<100:
        cmdstr='0'+str(value)
    else:
        cmdstr=str(value)
    return cmdstr

def pushCmd(ser,serstr): #push command to serial interface
    if flag_sim==1:
        return
    serbytes=serstr.encode()
    ser.write(serbytes)
    
def getTemp(ser):
    if flag_sim==1:
        global temp_sim
        temp_sim += led_sim + fan_sim
        temp_sim = max(temp_sim,15)
        #time.sleep(0.05)
        return temp_sim
    pushCmd(ser, 'T\n')
    return float(ser.readline())

def getLog(ser): #Read temperature log
    if flag_sim==1:
        return []
    pushCmd(ser, 'L\n')
    logstr1=ser.readline().decode().split()
    logtime1=[int(numeric_string) for numeric_string in logstr1[0::3]]
    logtime2=[float(numeric_string)/1000 for numeric_string in logstr1[1::3]]
    logtime1=[sum(x) for x in zip(logtime1,logtime2)]
    logtemp1=[float(numeric_string) for numeric_string in logstr1[2::3]]
    return [logtime1, logtemp1]

def getStat(ser):
    pushCmd(ser, 'S\n')
    stat=ser.readline()
    stat=stat.split()
    return int(stat[0])
    
def turnonLed(ser,value):
    if flag_sim==1:
        global led_sim
        led_sim=value/1000
        return
    value=np.clip(value,0,255)
    pushCmd(ser,encNum(value)+'1\n')

def turnoffLed(ser):
    if flag_sim==1:
        global led_sim
        led_sim=-0.01
        return
    pushCmd(ser,'0\n')
    
def turnonFan(ser):
    if flag_sim==1:
        global fan_sim
        fan_sim=-0.5
        return
    pushCmd(ser,'F\n')

def turnoffFan(ser):
    if flag_sim==1:
        global fan_sim
        fan_sim=-0.01
        return
    pushCmd(ser,'H\n')

def serial_ports():
    """Lists serial ports
    Raises:
    EnvironmentError:
    On unsupported or unknown platforms
    Returns:
    A list of available serial ports
    """
    if sys.platform.startswith('win'):
        ports = ['COM' + str(i + 1) for i in range(256)]
    elif sys.platform.startswith('linux') or sys.platform.startswith('cygwin'):
        # this is to exclude your current terminal "/dev/tty"
        ports = glob.glob('/dev/tty[A-Za-z]*')
    elif sys.platform.startswith('darwin'):
        #ports = glob.glob('/dev/tty.*') #tty not working in El Capitan
        ports = glob.glob('/dev/cu.*')
    else:
        raise EnvironmentError('Unsupported platform')
    result = []
    for port in ports:
        try:
            s = serial.Serial(port)
            s.close()
            result.append(port)
        except (OSError, serial.SerialException):
            pass
    return result

def serial_connect(baud=115200):
    print("Checking serial connections...")
    ports = serial_ports()
    if ports:
        print("Available serial ports:")
        for (i,p) in enumerate(ports):
            #print("[Not available] index:%d id:%s"%(i+1,p))
            if p.find('usbmodem')>0: #need to be updated to support multiplatform
                print("[Detected] index:%d id:%s"%(i+1,p))
                #ser = serial.Serial('/dev/cu.usbmodem1411', 9600)
                ser = serial.Serial(p, baud)
                time.sleep(1)
                global ser_global
                ser_global=ser
                return ser
    print("No ports available. Check serial connection and try again.")
    return 0

def plot_data(dtime, dtemp):
    plt.clf()
    #plt.plot(dtime[-1], dtemp[-1], '-ro')
    plt.plot(dtime, dtemp,'-ro')
    
    plt.xlabel('Time elapsed(s)')
    plt.ylabel('Temperature(C)')
    plt.title('PCR Thermal Cycling')
    plt.grid(True)
    axes = plt.gca()
    axes.set_ylim([0,100])
    #axes.set_xlim([dtime[-1*chop],dtime[-1]])
    
    #for a in annotate:
    #    #plt.annotate(a['label'], xy=(a['x'],a['y']))
    #    plt.annotate(a['label'], xy=(a['x'],a['y']), xycoords='data', 
    #                 textcoords='offset points', arrowprops=dict(arrowstyle="->")) 
    
    display.clear_output(wait=True)
    display.display(plt.gcf())

def plot_cont(dtime, dtemp): #controller variables
    plt.plot(dtime, in_cont,'-go', label="in")
    plt.plot(dtime, out_cont,'-bo', label="out")
    plt.plot(dtime, acc_cont,'-ro', label="acc")
    plt.xlabel('Time elapsed(s)')
    plt.ylabel('Controller variables')
    plt.title('PCR Thermal Cycling Control')
    plt.grid(True)
    plt.legend()
    axes = plt.gca()
    plt.show()

def update_data(dtime, dtemp, log):
    stat=0 #return zero if there is no update
    if dtime==[]:
        dtime_recent=0
    else:
        dtime_recent=dtime[-1]
    for i, t in enumerate(log[0]):
        if t>dtime_recent:
            stat=1
            dtime.append(t)
            dtemp.append(log[1][i])
    return [dtime, dtemp, stat]

def encodeCmd(dash):
    vphTemp = int(dash['phTemp'].value)
    vphTime = int(dash['phTime'].value)
    vphEn = dash['phEn'].value
    vdeTemp = int(dash['deTemp'].value)
    vdeTime = int(dash['deTime'].value)
    vanTemp = int(dash['anTemp'].value)
    vanTime = int(dash['anTime'].value)
    vexTemp = int(dash['exTemp'].value)
    vexTime = int(dash['exTime'].value)
    vexEn = dash['exEn'].value
    vnCy = int(dash['nCy'].value)
    vphP = int(dash['phP'].value)
    vphI = int(dash['phI'].value)
    vphD = int(dash['phD'].value)
    vdeP = int(dash['deP'].value)
    vdeI = int(dash['deI'].value)
    vdeD = int(dash['deD'].value)
    vanP = int(dash['anP'].value)
    vanI = int(dash['anI'].value)
    vanD = int(dash['anD'].value)
    vexP = int(dash['exP'].value)
    vexI = int(dash['exI'].value)
    vexD = int(dash['exD'].value)

    cmd=[]
    cmd.append({'type':'reset'})
    cmd.append({'type':'heat','target':vdeTemp}) #denature
    if vdeTime > 0:
        cmd.append({'type':'cont','target':vdeTemp,'time':vdeTime,'kp':vdeP,'ki':vdeI,'kd':vdeD})
    if vdeTemp >= vanTemp: #annealing
        cmd.append({'type':'cool','target':vanTemp}) 
    else:
        cmd.append({'type':'heat','target':vanTemp}) 
    
    cmd.append({'type':'loopback','amount':len(cmd)-1,'cycle':vnCy,'init':1})
    cmd.append({'type':'end'})
    return cmd

def runCmd(ser, cmd):
    pushCmd(ser,'R\n')
    for i,c in enumerate(cmd):  
        if c['type']=='reset':
            pushCmd(ser,'RC\n')
        if c['type']=='end':
            pushCmd(ser,'EC\n')
        elif c['type']=='loopback':
            pushCmd(ser,encNum(c['init'])+encNum(c['cycle'])+encNum(c['amount'])+'LC\n')
        elif c['type']=='heat':
            pushCmd(ser,encNum(c['target'])+'HC\n')
        elif c['type']=='cool':
            pushCmd(ser,encNum(c['target'])+'CC\n')
        elif c['type']=='cont':
            pushCmd(ser,encNum(c['kd'])+encNum(c['ki'])+encNum(c['kp'])+encNum(c['time'])+encNum(c['target'])+'OC\n')
    pushCmd(ser, 'X\n')  

def runPlot(ser):
    dtemp=[]
    dtime=[]
    update_stat=1
    for k in range(100000):
        [dtime, dtemp, update_stat]=update_data(dtime, dtemp, getLog(ser))
        if (update_stat==0) and (len(dtime)>0):
            break
        if getStat(ser)==0:
            for i in range(5):
                plot_data(dtime, dtemp)
                time.sleep(0.1)
            print('done')
            break
        if k%10==0:
            plot_data(dtime, dtemp)
    return [dtemp, dtime]

def tempBtn_clicked(b):
    global ser_global
    print('Measured temperature:',getTemp(ser_global))

def fanOnBtn_clicked(b):
    global ser_global
    turnonFan(ser_global)

def fanOffBtn_clicked(b):
    global ser_global
    turnoffFan(ser_global)

def ledOffBtn_clicked(b):
    global ser_global
    turnoffLed(ser_global)

def plotDash(ser):
    defTemp=70
    defTime=0
    defP=10
    defI=2
    defD=1
    defC=30
    phTemp = widgets.Text(description="Temp (C)", width=200, value=str(defTemp))
    phTime = widgets.Text(description="Time (s)", width=200, value=str(defTime))
    phEn = widgets.Checkbox(description='Enabled?')
    phPanel = widgets.VBox([widgets.Button(description='Preheat'), phTemp, phTime, phEn]) 
    deTemp = widgets.Text(description="Temp (C)", width=200, value=str(90))
    deTime = widgets.Text(description="Time (s)", width=200, value=str(defTime))
    dePanel = widgets.VBox([widgets.Button(description='Denature'), deTemp, deTime]) 
    anTemp = widgets.Text(description="Temp (C)", width=200, value=str(68))
    anTime = widgets.Text(description="Time (s)", width=200, value=str(defTime))
    anPanel = widgets.VBox([widgets.Button(description='Annealing'), anTemp, anTime]) 
    exTemp = widgets.Text(description="Temp (C)", width=200, value=str(defTemp))
    exTime = widgets.Text(description="Time (s)", width=200, value=str(defTime))
    exEn = widgets.Checkbox(description='Enabled?')
    exPanel = widgets.VBox([widgets.Button(description='Extension'), phTemp, phTime, phEn]) 
    nCy = widgets.Text(description="Cycles", width=200, value=str(defC))
    auxPanel = widgets.VBox([nCy]) 
    phP = widgets.Text(description="Kp", width=200, value=str(defP))
    phI = widgets.Text(description="Ki", width=200, value=str(defI))
    phD = widgets.Text(description="Kd", width=200, value=str(defD))
    phPIDPanel = widgets.VBox([widgets.Button(description='PreheatPID'), phP, phI, phD]) 
    deP = widgets.Text(description="Kp", width=200, value=str(defP))
    deI = widgets.Text(description="Ki", width=200, value=str(defI))
    deD = widgets.Text(description="Kd", width=200, value=str(defD))
    dePIDPanel = widgets.VBox([widgets.Button(description='DenaturePID'), deP, deI, deD]) 
    anP = widgets.Text(description="Kp", width=200, value=str(defP))
    anI = widgets.Text(description="Ki", width=200, value=str(defI))
    anD = widgets.Text(description="Kd", width=200, value=str(defD))
    anPIDPanel = widgets.VBox([widgets.Button(description='AnnealingPID'), anP, anI, anD]) 
    exP = widgets.Text(description="Kp", width=200, value=str(defP))
    exI = widgets.Text(description="Ki", width=200, value=str(defI))
    exD = widgets.Text(description="Kd", width=200, value=str(defD))
    exPIDPanel = widgets.VBox([widgets.Button(description='ExtensionPID'), exP, exI, exD]) 
    tempBtn=widgets.Button(description='Meas Temp')
    tempBtn.on_click(tempBtn_clicked)
    fanOnBtn=widgets.Button(description='Fan On')
    fanOnBtn.on_click(fanOnBtn_clicked)
    fanOffBtn=widgets.Button(description='Fan Off')
    fanOffBtn.on_click(fanOffBtn_clicked)
    ledOffBtn=widgets.Button(description='LED Off')
    ledOffBtn.on_click(ledOffBtn_clicked)
    controlPanel = widgets.VBox([widgets.HBox([phPanel,dePanel]),
                             widgets.HBox([anPanel,exPanel]),
                             widgets.HBox([auxPanel]),
                             widgets.HBox([phPIDPanel,dePIDPanel]),
                             widgets.HBox([anPIDPanel,exPIDPanel]),
                             widgets.HBox([tempBtn, fanOnBtn, fanOffBtn, ledOffBtn])])
    controlPanel_dict = dict(phTemp = phTemp,
        phTime = phTime,
        phEn = phEn,
        deTemp = deTemp,
        deTime = deTime,
        anTemp = anTemp,
        anTime = anTime,
        exTemp = exTemp,
        exTime = exTime,
        exEn = exEn,
        nCy = nCy,
        phP = phP,
        phI = phI,
        phD = phD,
        deP = deP,
        deI = deI,
        deD = deD,
        anP = anP,
        anI = anI,
        anD = anD,
        exP = exP,
        exI = exI,
        exD = exD)

    display.display(controlPanel)
    return controlPanel_dict
       
