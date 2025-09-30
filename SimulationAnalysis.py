import numpy as np
import pandas as pd
from scipy.signal import firwin

import matplotlib.pyplot as plt

def loaddata(filename=None):
    """
    Carrega arquivo CSV com colunas: eixox,eixoy,eixoz,gps_fix,gps_speed,gps_direction,gps_alt,gps_rtc
    """
    if filename is None:
        raise ValueError("Arquivo não fornecido")
    df = pd.read_csv(filename)
    return {
        'eixox': df['eixox'].values,
        'eixoy': df['eixoy'].values,
        'eixoz': df['eixoz'].values,
        'gps_fix': df['gps_fix'].values,
        'gps_speed': df['gps_speed'].values,
        'gps_direction': df['gps_direction'].values,
        'gps_alt': df['gps_alt'].values,
        'gps_rtc': df['gps_rtc'].values,
        'contreg': df.index.values
    }

def attitude_estimation_v7_gimbal_lock(filename=""):
    data = loaddata(filename)

    # Parâmetros
    fGPS = 1
    g_a = np.array([0,0,1])
    phi_a = theta_a = psi_a = 1234
    vlow = 15
    ahigh = 0.22
    alow = 0.08
    
    # Filtro FIR
    fc = 0.3
    fs = 20
    filter_order = 63
    filter_coeff = firwin(filter_order+1, fc/(fs/2))
    filter_buffer = np.zeros((filter_order+1, 3))

    # plt.figure(figsize=(10,5))
    # plt.plot(filter_coeff)
    # plt.show()

    # Buffers GPS
    GPS_filter_delay = 1.5
    GPS_filter_delay_samples = int(np.ceil(GPS_filter_delay * fs))
    max_duration_event = 10
    Ngps = int(np.ceil((GPS_filter_delay + max_duration_event) * fGPS)) + 1
    GPS_buffer = np.zeros((Ngps,5))
    gps_buffer_cont = 0
    gps_nsamp = 0

    # Buffers aceleração
    event = 0
    Naccel = int(max_duration_event * fs)
    Accel_buffer = np.zeros((Naccel,3))
    accel_buffer_cont = 0
    Naccel_grav = 200
    Accel_buffer_grav = np.zeros((Naccel_grav,3))
    accel_buffer_grav_cont = 0
    max_accel_dev = 0
    pos_max_accel_dev = 1

    for n in range(len(data['contreg'])):
        # Atualiza buffer do filtro FIR
        filter_buffer[:-1,:] = filter_buffer[1:,:]
        filter_buffer[-1,:] = [data['eixox'][n], data['eixoy'][n], data['eixoz'][n]]
        if n >= filter_order:
            accel_f = np.dot(filter_coeff, filter_buffer)

            if phi_a == 1234 or theta_a == 1234:
                media = np.mean(filter_buffer[:64,:], axis=0)
                var = np.std(filter_buffer[:64,:], axis=0)
                accel_dev = np.linalg.norm(var)

                if accel_dev < alow:
                    accel_buffer_grav_cont += 1
                    Accel_buffer_grav[accel_buffer_grav_cont-1,:] = accel_f
                else:
                    accel_buffer_grav_cont = 0

                if accel_buffer_grav_cont == Naccel_grav:
                    phi_a, theta_a, g_a = Roll_Pitch_Estimation(Accel_buffer_grav)
            
            # Avaliação de picos
            if phi_a != 1234 and theta_a != 1234:
                accel_dev = np.linalg.norm(accel_f - g_a)
                if accel_dev > ahigh:
                    if event == 0:
                        event = 1
                        accel_buffer_cont = 1
                    else:
                        accel_buffer_cont += 1
                        accel_buffer_cont = min(accel_buffer_cont, Naccel)

                    Accel_buffer[:-1,:] = Accel_buffer[1:,:]
                    Accel_buffer[-1,:] = accel_f

                    if accel_dev > max_accel_dev:
                        max_accel_dev = accel_dev
                        pos_max_accel_dev = accel_buffer_cont
                else:
                    if event == 1:
                        short_accel_buffer = Accel_buffer[Naccel-accel_buffer_cont:,:]
                        psi_a_est = Yaw_Estimation(phi_a, theta_a, g_a,
                                                    GPS_filter_delay, fs,
                                                    GPS_buffer, gps_buffer_cont,
                                                    gps_nsamp, short_accel_buffer,
                                                    max_accel_dev, pos_max_accel_dev)
                        if psi_a_est != 1234:
                            psi_a = psi_a_est
                        if (gps_buffer_cont > 0):
                            if (GPS_buffer[gps_buffer_cont,3] == 1234):
                                gps_buffer_cont = 0

                    event = 0
                    max_accel_dev = 0
                    pos_max_accel_dev = 1

            # Atualiza buffer GPS
            m = n - GPS_filter_delay_samples
            if data['gps_fix'][m] == 3 and data['gps_speed'][m] >= vlow:
                if gps_buffer_cont == 0:
                    GPS_buffer[gps_buffer_cont, :] = [data['gps_rtc'][m], 
                                                      data['gps_speed'][m],
                                                      data['gps_direction'][m], 
                                                      data['gps_alt'][m], 
                                                      0]
                    gps_nsamp = 0
                    gps_buffer_cont += 1
                elif data['gps_rtc'][m] != GPS_buffer[gps_buffer_cont-1,0]:
                    if gps_buffer_cont == Ngps:
                        GPS_buffer[:-1,:] = GPS_buffer[1:,:]
                        gps_buffer_cont = Ngps-1
                    GPS_buffer[gps_buffer_cont, :] = [data['gps_rtc'][m], 
                                                      data['gps_speed'][m],
                                                      data['gps_direction'][m], 
                                                      data['gps_alt'][m], 
                                                      gps_nsamp]
                    gps_nsamp = 0
                    gps_buffer_cont += 1
            else:
                if event == 0:
                    gps_buffer_cont = 0
                elif gps_buffer_cont > 0 and data['gps_rtc'][m] != GPS_buffer[gps_buffer_cont-1,0]:
                    if gps_buffer_cont == Ngps:
                        GPS_buffer[:-1,:] = GPS_buffer[1:,:]
                        gps_buffer_cont = Ngps-1
                    GPS_buffer[gps_buffer_cont,:] = [data['gps_rtc'][m], 
                                                     0, 
                                                     1234, 
                                                     1234, 
                                                     gps_nsamp]
                    gps_nsamp = 0
                    gps_buffer_cont += 1

            gps_nsamp += 1

    print(f'phi_a = {np.degrees(phi_a):.2f} deg, theta_a = {np.degrees(theta_a):.2f} deg, psi_a = {np.degrees(psi_a):.2f} deg')
    return phi_a, theta_a, psi_a

def accel_GPS(GPS_buffer):
    """
    Estima aceleração a partir do buffer de GPS no referencial NED.
    
    Parâmetros:
        GPS_buffer : np.array(N,5) com colunas [rtc, speed, heading, alt, nsamp]
        
    Retorna:
        axyz : aceleração estimada (Nx3) normalizada por g0
        psi_v : ângulo de proa correspondente (rad)
    """
    
    Ngps = GPS_buffer.shape[0]
    
    # Inicializa vetor de velocidades no plano XY
    vxy = np.zeros((Ngps, 2))
    
    # Converte velocidade de km/h para m/s e heading de graus para rad
    vxy[:,0] = GPS_buffer[:,1]*1000/3600 * np.cos(np.radians(GPS_buffer[:,2]))
    vxy[:,1] = GPS_buffer[:,1]*1000/3600 * np.sin(np.radians(GPS_buffer[:,2]))
    
    # Gravidade
    g0 = 9.80665
    
    # Diferença finita para estimar aceleração
    dt = np.diff(GPS_buffer[:,0])  # diferenças de tempo
    # Previne divisão por zero
    dt[dt==0] = 1e-6
    axyz_xy = np.diff(vxy, axis=0) / dt[:, None] / g0  # Nx2
    
    # Considera veículo no plano, adiciona coluna Z = 0
    axyz = np.hstack([axyz_xy, np.zeros((axyz_xy.shape[0],1))])
    
    # ângulo de proa correspondente
    psi_v = np.arctan2(vxy[:-1,1], vxy[:-1,0])
    
    # Transpõe para manter consistência com MATLAB (3xN)
    return axyz.T, psi_v

def Roll_Pitch_Estimation(accel_buffer):
    """
    Calcula os ângulos de rolamento (phi) e arfagem (theta)
    a partir de um buffer de acelerações filtradas.
    
    accel_buffer: numpy array de forma (N,3)
    Retorna:
        phi_a: rolamento em rad
        theta_a: arfagem em rad
        an: vetor normalizado de gravidade estimado
    """
    # Media das acelerações filtradas
    an = np.mean(accel_buffer[:, 0:3], axis=0)
    
    # Normaliza o vetor
    an = an / np.linalg.norm(an)
    
    # Calcula os ângulos
    phi_a = np.arctan2(-an[1], -an[2])
    theta_a = np.arcsin(an[0])
    
    print(f'phi_a = {np.degrees(phi_a):.2f} deg, theta_a = {np.degrees(theta_a):.2f} deg')
    
    return phi_a, theta_a, an

import numpy as np

def Yaw_Estimation(phi_a, theta_a, g_a,
                   filter_delay,
                   fs,
                   GPS_buffer, gps_buffer_cont, gps_nsamp,
                   Accel_buffer,
                   max_accel_dev, pos_max_accel_dev):
    """
    Estima o ângulo de guinada (yaw) a partir do buffer de GPS e
    aceleração do veículo.

    Parâmetros:
        phi_a, theta_a : ângulos de rolamento e arfagem
        g_a : vetor gravidade estimado
        filter_delay, fs : parâmetros do filtro
        GPS_buffer : buffer de dados GPS (Nx5)
        gps_buffer_cont : índice de contagem do buffer GPS
        gps_nsamp : amostras desde último GPS
        Accel_buffer : buffer de aceleração
        max_accel_dev, pos_max_accel_dev : controle de evento
    Retorna:
        psi_a : ângulo de guinada estimado (rad)
    """
    
    psi_a = 1234  # valor padrão caso não seja possível estimar
    
    if gps_buffer_cont == 0:
        return psi_a
    
    ntotal = 0
    end_gps = gps_buffer_cont
    nsamples = gps_nsamp
    
    # Ajuste do buffer de GPS relativo ao evento
    event_length = Accel_buffer.shape[0]
    k = end_gps
    last_sample = event_length - (gps_nsamp - ntotal)  # ntotal=0
    
    while last_sample > 0 and k > 0:
        aux = last_sample - GPS_buffer[k,4]  # coluna 5 em MATLAB = índice 4
        GPS_buffer[k,4] = last_sample
        last_sample = aux
        k -= 1
        
    start_gps = k + 1 if k < gps_buffer_cont-1 else gps_buffer_cont-1
    
    GPS_event_buffer = GPS_buffer[start_gps:gps_buffer_cont,:]
    
    if GPS_event_buffer.shape[0] == 1:
        return psi_a  # impossível estimar com apenas 1 ponto
    
    # Variacao total da guinada e altitude
    min_alt = np.min(GPS_event_buffer[:,3])
    max_alt = np.max(GPS_event_buffer[:,3])
    alt_variation = max_alt - min_alt
    
    yaw_variation = 0
    for i in range(1, GPS_event_buffer.shape[0]):
        v1 = np.array([np.cos(np.radians(GPS_event_buffer[i-1,2])),
                       np.sin(np.radians(GPS_event_buffer[i-1,2]))])
        v2 = np.array([np.cos(np.radians(GPS_event_buffer[i,2])),
                       np.sin(np.radians(GPS_event_buffer[i,2]))])
        vr = v2 - v1
        delta_yaw = 2 * np.arcsin(np.clip(np.linalg.norm(vr)/2, -1, 1))
        yaw_variation += delta_yaw
    yaw_variation = np.degrees(yaw_variation)
    
    # Calcula aceleração via GPS
    axyz, psi_v = accel_GPS(GPS_event_buffer)
    
    # Matrizes de rotação para phi e theta
    R_phi = np.array([[1, 0, 0],
                      [0, np.cos(phi_a), np.sin(phi_a)],
                      [0, -np.sin(phi_a), np.cos(phi_a)]])
    
    R_theta = np.array([[np.cos(theta_a), 0, -np.sin(theta_a)],
                        [0, 1, 0],
                        [np.sin(theta_a), 0, np.cos(theta_a)]])
    
    # Cálculo do yaw para cada ponto
    psi_list = []
    residuo_list = []
    for k in range(axyz.shape[1]):
        R_psi = np.array([[np.cos(psi_v[k]), np.sin(psi_v[k]), 0],
                          [-np.sin(psi_v[k]), np.cos(psi_v[k]), 0],
                          [0, 0, 1]])
        
        am = Accel_buffer[GPS_event_buffer[k,4].astype(int), :]
        w = R_theta.T @ R_phi.T @ (am - g_a)
        v = R_psi @ axyz[:,k]
        
        psi = np.arctan2(v[1]*w[0] - v[0]*w[1], v[0]*w[0] + v[1]*w[1])
        psi_list.append(psi)
        
        R_psi_a = np.array([[np.cos(psi), np.sin(psi), 0],
                            [-np.sin(psi), np.cos(psi), 0],
                            [0,0,1]])
        residuo_vec = (am - g_a) - (R_phi @ R_theta @ R_psi_a @ R_psi @ axyz[:,k])
        residuo_list.append(np.linalg.norm(residuo_vec))
    
    psi_list = np.array(psi_list)
    residuo = np.mean(residuo_list)
    psi_a = np.mean(psi_list)
    
    if GPS_event_buffer.shape[0] > 2 and residuo < 0.1:
        print(f'psi_a = {np.degrees(psi_a):.2f} deg, Yaw var: {yaw_variation:.2f} deg, Alt var: {alt_variation:.2f} m')
    else:
        psi_a = 1234
    
    return psi_a



a, b, c = attitude_estimation_v7_gimbal_lock("fake_vehicle_data.csv")