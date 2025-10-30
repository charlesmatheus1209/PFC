using DataCollector.Model;
using Microsoft.Maui.Devices.Sensors;
using System.Text;

namespace DataCollector.Services
{
    public class AccelerometerService
    {
        public event Action<AccelerometerDataModel> OnAccelerometerChanged;


        public void Start()
        {
            if (Accelerometer.Default.IsSupported && !Accelerometer.Default.IsMonitoring)
            {
                Accelerometer.Default.ReadingChanged += Accelerometer_ReadingChanged;
                Accelerometer.Default.Start(SensorSpeed.UI);
            }
        }

        public void Stop()
        {
            if (Accelerometer.Default.IsSupported && Accelerometer.Default.IsMonitoring)
            {
                Accelerometer.Default.Stop();
                Accelerometer.Default.ReadingChanged -= Accelerometer_ReadingChanged;
            }
        }

        private void Accelerometer_ReadingChanged(object sender, AccelerometerChangedEventArgs e)
        {
            var reading = e.Reading.Acceleration;

            AccelerometerDataModel data = new AccelerometerDataModel();

            data.xAcceleration = reading.X;
            data.yAcceleration = reading.Y;
            data.zAcceleration = reading.Z;

            OnAccelerometerChanged?.Invoke(data);
        }
    }
}
