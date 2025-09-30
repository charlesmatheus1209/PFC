import pandas as pd
import numpy as np

# Número de amostras
N = 500

# Simulação do acelerômetro (pequenas variações em torno de gravidade)
eixox = 0.01 * np.random.randn(N)
eixoy = 0.01 * np.random.randn(N)
eixoz = 1 + 0.01 * np.random.randn(N)  # gravidade em Z

# Dados GPS simulados
gps_fix = np.ones(N, dtype=int) * 3
gps_speed = 20 + 2 * np.random.randn(N)       # km/h
gps_direction = np.random.uniform(0, 360, N) # graus
gps_alt = 50 + 0.5 * np.random.randn(N)      # metros
gps_rtc = np.arange(N)                        # tempo em segundos

# Índice da amostra
contreg = np.arange(N)

# Criação do DataFrame
df = pd.DataFrame({
    'contreg': contreg,
    'eixox': eixox,
    'eixoy': eixoy,
    'eixoz': eixoz,
    'gps_fix': gps_fix,
    'gps_speed': gps_speed,
    'gps_direction': gps_direction,
    'gps_alt': gps_alt,
    'gps_rtc': gps_rtc
})

# Salvar em CSV
df.to_csv('fake_vehicle_data.csv', index=False)
print("Arquivo 'fake_vehicle_data.csv' criado com sucesso!")
