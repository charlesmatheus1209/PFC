using DataCollector.Model;
using Microsoft.Maui.Devices.Sensors;
using System.Threading;

#if ANDROID
using Android.Locations;
using Android.Content;
using Android.OS;
#endif

namespace DataCollector.Services
{
    public class GPSService
    {
        public event Action<GPSDataModel> OnLocationChanged;
        public event Action<int> OnSatelliteCountChanged;
        public event Action<string> OnFixTypeChanged;

        private bool _isRunning = false;
        private CancellationTokenSource _cts;

        #if ANDROID
        private LocationManager _locationManager;
        private GnssCallback _gnssCallback;
        #endif

        public async Task Start()
        {
            try
            {
                if (_isRunning)
                    return;

                _isRunning = true;
                _cts = new CancellationTokenSource();

                #if ANDROID
                InitializeAndroidGpsMonitoring();
                #endif

                await Task.Run(async () =>
                {
                    while (_isRunning && !_cts.Token.IsCancellationRequested)
                    {
                        try
                        {
                            var request = new GeolocationRequest(GeolocationAccuracy.Best, TimeSpan.FromMilliseconds(200));
                            var location = await Geolocation.GetLocationAsync(request, _cts.Token);

                            if (location != null)
                            {
                                var data = new GPSDataModel
                                {
                                    GPSTimestamp = location.Timestamp,
                                    Latitude = location.Latitude,
                                    Longitude = location.Longitude,
                                    Altitude = location.Altitude ?? 0,
                                    Speed = location.Speed ?? 0,
                                    Course = location.Course ?? 0
                                };

                                #if ANDROID
                                data.SatelliteCount = _gnssCallback?.LastSatelliteCount ?? 0;
                                data.FixType = _gnssCallback?.LastFixType ?? "Desconhecido";
                                #endif

                                OnLocationChanged?.Invoke(data);
                            }
                        }
                        catch (Exception ex)
                        {
                            Console.WriteLine($"Erro ao obter localização: {ex.Message}");
                        }

                        await Task.Delay(200, _cts.Token);
                    }
                });
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Erro ao iniciar GPS: {ex.Message}");
            }
        }

        public void Stop()
        {
            try
            {
                _isRunning = false;
                _cts?.Cancel();
                _cts?.Dispose();

                #if ANDROID
                if (_gnssCallback != null)
                    _locationManager.UnregisterGnssStatusCallback(_gnssCallback);
                #endif
            }
            catch (Exception ex)
            {
                Console.WriteLine($"Erro ao parar GPS: {ex.Message}");
            }
        }

        #if ANDROID
        private void InitializeAndroidGpsMonitoring()
        {
            var context = Android.App.Application.Context;
            _locationManager = (LocationManager)context.GetSystemService(Context.LocationService);

            _gnssCallback = new GnssCallback(OnSatelliteCountChanged, OnFixTypeChanged);
            _locationManager.RegisterGnssStatusCallback(_gnssCallback);
        }

        private class GnssCallback : GnssStatus.Callback
        {
            private readonly Action<int> _satelliteCallback;
            private readonly Action<string> _fixCallback;

            public int LastSatelliteCount { get; private set; } = 0;
            public string LastFixType { get; private set; } = "Sem Fix";

            public GnssCallback(Action<int> satCb, Action<string> fixCb)
            {
                _satelliteCallback = satCb;
                _fixCallback = fixCb;
            }

            public override void OnSatelliteStatusChanged(GnssStatus status)
            {
                if (status == null)
                    return;

                int count = 0;
                for (int i = 0; i < status.SatelliteCount; i++)
                {
                    if (status.HasEphemerisData(i))
                        count++;
                }

                LastSatelliteCount = count;
                _satelliteCallback?.Invoke(count);
            }

            public override void OnStarted()
            {
                LastFixType = "Aguardando sinal";
                _fixCallback?.Invoke(LastFixType);
            }

            public override void OnFirstFix(int ttffMillis)
            {
                LastFixType = "3D Fix";
                _fixCallback?.Invoke(LastFixType);
            }

            public override void OnStopped()
            {
                LastFixType = "Sem sinal";
                _fixCallback?.Invoke(LastFixType);
            }
        }

        // Função auxiliar para validar o GPS: retorna true se houver fix 3D e pelo menos 4 satélites
        public bool IsValidGPS()
        {
            if (_gnssCallback == null)
                return false;

            return _gnssCallback.LastFixType == "3D Fix" && _gnssCallback.LastSatelliteCount >= 4;
        }
        #endif
    }
}
