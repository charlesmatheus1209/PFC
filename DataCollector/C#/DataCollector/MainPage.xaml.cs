using DataCollector.Model;
using DataCollector.Services;
using DataCollector.Services;

namespace DataCollector
{
    public partial class MainPage : ContentPage
    {
        private PeriodicTask _task;
        LogService _log;

        public MainPage()
        {
            InitializeComponent();

            _task = new PeriodicTask();
            _task.OnDataChanged += UpdateLabels;

            _log = new LogService();
        }

        private void UpdateLabels(DataModel data)
        {
            MainThread.BeginInvokeOnMainThread(() =>
            {
                // Acelerômetro
                xLabel.Text = $"X: {data.accelerometerData?.xAcceleration:F3}";
                yLabel.Text = $"Y: {data.accelerometerData?.yAcceleration:F3}";
                zLabel.Text = $"Z: {data.accelerometerData?.zAcceleration:F3}";

                // GPS
                Timestamp.Text = $"Timestamp: {data.gpsData.GPSTimestamp.ToString()}";
                latitudeLabel.Text = $"Latitude: {data.gpsData?.Latitude:F6}";
                longitudeLabel.Text = $"Longitude: {data.gpsData?.Longitude:F6}";
                altitudeLabel.Text = $"Altitude: {data.gpsData?.Altitude:F2} m";
                speedLabel.Text = $"Velocidade: {data.gpsData?.Speed:F2} m/s";
                courseLabel.Text = $"Course: {data.gpsData?.Course:F2} º";
                satellitesLabel.Text = $"Satelites: {data.gpsData?.SatelliteCount:F2}";
                fixLabel.Text = $"Fix: {data.gpsData?.FixType:F2}";
            });
            
            _log.LogData(data);
        }

        private async void OnStartClicked(object sender, EventArgs e)
        {
            await _task.Start(100); // Atualiza a cada 0,1 segundo
            DeviceDisplay.KeepScreenOn = true;

        }

        private void OnStopClicked(object sender, EventArgs e)
        {
            _task.Stop();
            DeviceDisplay.KeepScreenOn = false;
        }



    }
}

