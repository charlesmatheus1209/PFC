using DataCollector.Model;
using System.Timers;

namespace DataCollector.Services
{
    public class PeriodicTask
    {
        private System.Timers.Timer _timer;
        private readonly AccelerometerService _accelerometerService;
        private readonly GPSService _gpsService;

        public event Action<DataModel> OnDataChanged;

        private AccelerometerDataModel _accelerometerData = new();
        private GPSDataModel _gpsData = new();

        public PeriodicTask()
        {
            _accelerometerService = new AccelerometerService();
            _gpsService = new GPSService();

            // Eventos de atualização dos sensores
            _accelerometerService.OnAccelerometerChanged += OnAccelerometerChanged;
            _gpsService.OnLocationChanged += OnLocationChanged;
        }

        /// <summary>
        /// Inicia a coleta de dados e o timer periódico.
        /// </summary>
        public async Task Start(int milliseconds)
        {
            // Inicializa o timer
            _timer = new System.Timers.Timer(milliseconds);
            _timer.Elapsed += OnTimerEvent;
            _timer.AutoReset = true;
            _timer.Enabled = true;

            // Inicia sensores
            _accelerometerService.Start();
            _ = _gpsService.Start(); // roda em background (não bloqueante)
        }

        /// <summary>
        /// Executado a cada período definido.
        /// </summary>
        private void OnTimerEvent(object sender, ElapsedEventArgs e)
        {
            var data = new DataModel
            {
                accelerometerData = _accelerometerData,
                gpsData = _gpsData
            };

            OnDataChanged?.Invoke(data);
        }

        /// <summary>
        /// Para a coleta e o timer.
        /// </summary>
        public void Stop()
        {
            _timer?.Stop();
            _timer?.Dispose();

            _accelerometerService.Stop();
            _gpsService.Stop();
        }

        /// <summary>
        /// Recebe atualizações do acelerômetro.
        /// </summary>
        private void OnAccelerometerChanged(AccelerometerDataModel accelerometerData)
        {
            _accelerometerData = accelerometerData;
        }

        /// <summary>
        /// Recebe atualizações do GPS.
        /// </summary>
        private void OnLocationChanged(GPSDataModel gpsData)
        {
            if (gpsData != null)
                _gpsData = gpsData;
        }
    }
}
