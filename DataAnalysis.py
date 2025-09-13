import numpy as np
import matplotlib.pyplot as plt


## Reamostrar o dado de posição para algo mais próximo da realidade (10Hz)
# - Resample (matlab), mas por enquanto só deleta as amostras 
def readSensorFile(file):
    time = []
    accelerationX = []
    accelerationY = []
    accelerationZ = []
    positionX = []
    positionY = []
    positionZ = []

    i = 0
    for x in file:
        if i!=0:
            values = x.split(";")

            time.append(float(values[0]))
            accelerationX.append(float(values[1]))
            accelerationY.append(float(values[2]))
            accelerationZ.append(float(values[3]))
            positionX.append(float(values[4]))
            positionY.append(float(values[5]))
            positionZ.append(float(values[6]))
        i=1
    
    return time, accelerationX, accelerationY, accelerationZ, positionX, positionY, positionZ

def calcVeloc(time, posiX, posiY, posiZ):
    dt = np.diff(time)
    dx = np.diff(posiX)
    dy = np.diff(posiY)
    dz = np.diff(posiZ)

    return dx/dt, dy/dt, dz/dt

def plotFigures():
    # cria figuras
    plt.figure(figsize=(12, 8))

    # Aceleração X
    plt.subplot(3, 1, 1)
    plt.plot(carTime, carAcceX, label="Carro - X", color="blue")
    plt.plot(phoneTime, phoneAcceX, label="Celular - X", color="cyan", linestyle="--")
    plt.title("Aceleração nos Eixos")
    plt.ylabel("Aceleração X (m/s²)")
    plt.legend()

    # Aceleração Y
    plt.subplot(3, 1, 2)
    plt.plot(carTime, carAcceY, label="Carro - Y", color="red")
    plt.plot(phoneTime, phoneAcceY, label="Celular - Y", color="orange", linestyle="--")
    plt.ylabel("Aceleração Y (m/s²)")
    plt.legend()

    # Aceleração Z
    plt.subplot(3, 1, 3)
    plt.plot(carTime, carAcceZ, label="Carro - Z", color="green")
    plt.plot(phoneTime, phoneAcceZ, label="Celular - Z", color="lime", linestyle="--")
    plt.xlabel("Tempo (s)")
    plt.ylabel("Aceleração Z (m/s²)")
    plt.legend()

    plt.tight_layout()


    # cria figuras
    plt.figure(figsize=(12, 8))

    # Aceleração X
    plt.subplot(3, 1, 1)
    plt.plot(carTime, carPosiX, label="Carro - X", color="blue")
    plt.plot(phoneTime, phonePosiX, label="Celular - X", color="cyan", linestyle="--")
    plt.title("Posição nos Eixos")
    plt.ylabel("Posição X (m/s²)")
    plt.legend()

    # Aceleração Y
    plt.subplot(3, 1, 2)
    plt.plot(carTime, carPosiY, label="Carro - Y", color="red")
    plt.plot(phoneTime, phonePosiY, label="Celular - Y", color="orange", linestyle="--")
    plt.ylabel("Posição Y (m/s²)")
    plt.legend()

    # Aceleração Z
    plt.subplot(3, 1, 3)
    plt.plot(carTime, carPosiZ, label="Carro - Z", color="green")
    plt.plot(phoneTime, phonePosiZ, label="Celular - Z", color="lime", linestyle="--")
    plt.xlabel("Tempo (s)")
    plt.ylabel("Posição Z (m/s²)")
    plt.legend()

    plt.tight_layout()


    plt.figure(figsize=(10,5))
    plt.plot(carTime[:-1], carVx, label="Velocidade X")
    plt.plot(carTime[:-1], carVy, label="Velocidade Y")
    plt.plot(carTime[:-1], carVz, label="Velocidade Z")
    plt.xlabel("Tempo (s)")
    plt.ylabel("Velocidade (m/s)")
    plt.title("Velocidade do Carro")
    plt.legend()
    plt.show()

carData = open("C:\\Users\\charl\\Downloads\\CoppeliaSim_Edu_V4_1_0_Win\\SensorDataPFC\\CarAccelerometerData.txt")
phoneData = open("C:\\Users\\charl\\Downloads\\CoppeliaSim_Edu_V4_1_0_Win\\SensorDataPFC\\PhoneAccelerometerData.txt")

_carTime, _carAcceX, _carAcceY, _carAcceZ, _carPosiX, _carPosiY, _carPosiZ = readSensorFile(carData)
_phoneTime, _phoneAcceX, _phoneAcceY, _phoneAcceZ, _phonePosiX, _phonePosiY, _phonePosiZ = readSensorFile(phoneData)

carTime = np.matrix(_carTime).transpose()
carAcceX = np.matrix(_carAcceX).transpose()
carAcceY = np.matrix(_carAcceY).transpose()
carAcceZ = np.matrix(_carAcceZ).transpose()
carPosiX = np.matrix(_carPosiX).transpose()
carPosiY = np.matrix(_carPosiY).transpose()
carPosiZ = np.matrix(_carPosiZ).transpose()
phoneTime = np.matrix(_phoneTime).transpose()
phoneAcceX = np.matrix(_phoneAcceX).transpose()
phoneAcceY = np.matrix(_phoneAcceY).transpose()
phoneAcceZ = np.matrix(_phoneAcceZ).transpose()
phonePosiX = np.matrix(_phonePosiX).transpose()
phonePosiY = np.matrix(_phonePosiY).transpose()
phonePosiZ = np.matrix(_phonePosiZ).transpose()



carVx, carVy, carVz = calcVeloc(carTime, carPosiX, carPosiY, carPosiZ)
phoneVx, phoneVy, phoneVz = calcVeloc(phoneTime, phonePosiX, phonePosiY, phonePosiZ)

phoneAcce = np.concatenate((phoneAcceX, phoneAcceY, phoneAcceZ), axis=1)

## Estimating paramethers phi_a, theta_a, psi_a

g0 = 9.80665


# 1. Non-accelerated state (NS):

anX = []
anY = []
anZ = []
Nf = 1

for k in range(len(phoneAcce[:,0])):
    anX = "teste"#????

# for k in range(10):
#     print(k) 

# 2. Accelerated state (AS):

# 3. Unknown state (US)

## Computing the Euler Angles
# Theta_a = pi/2

theta_a = np.pi/2

R_theta = np.matrix([[1, 0, 0],[0, np.cos(theta_a), -np.sin(theta_a)], [0, np.sin(theta_a), np.cos(theta_a)]])






#plotFigures()



