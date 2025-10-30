using DataCollector.Model;
using System;
using System.IO;
using System.Text;

namespace DataCollector.Services
{
    public class LogService
    {
        private readonly object _lock = new();
        private readonly string _filePath;

        public LogService()
        {
#if ANDROID
            // Caminho base: /storage/emulated/0/Download/
            string downloadsPath = Android.OS.Environment
                .GetExternalStoragePublicDirectory(Android.OS.Environment.DirectoryDownloads)
                .AbsolutePath;

            // Cria subpasta dentro de Downloads
            string folderPath = Path.Combine(downloadsPath, "DataCollector");

            if (!Directory.Exists(folderPath))
                Directory.CreateDirectory(folderPath);

            // Define o caminho completo do arquivo CSV
            _filePath = Path.Combine(folderPath, $"sensor_data_{DateTime.Now:yyyyMMdd_HHmmss}.csv");
#else
            // Caminho padrão para outras plataformas (ex: Windows, iOS)
            string folderPath = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.MyDocuments), "DataCollector");

            if (!Directory.Exists(folderPath))
                Directory.CreateDirectory(folderPath);

            _filePath = Path.Combine(folderPath, $"sensor_data_{DateTime.Now:yyyyMMdd_HHmmss}.csv");
#endif

            // Cabeçalho atualizado com todos os campos
            string header = "Timestamp," +
                            "X_Acceleration,Y_Acceleration,Z_Acceleration," +
                            "Latitude,Longitude,Altitude,Speed,Course," +
                            "SatelliteCount,FixType,GPSTimestamp\n";

            File.WriteAllText(_filePath, header, Encoding.UTF8);
        }

        public void LogData(DataModel data)
        {
            if (data == null || data.gpsData == null || data.accelerometerData == null)
                return;

            // Monta uma linha do CSV com todos os parâmetros
            string line =
                $"{DateTime.Now:yyyy-MM-dd HH:mm:ss.fff}," +
                $"{data.accelerometerData.xAcceleration:F4}," +
                $"{data.accelerometerData.yAcceleration:F4}," +
                $"{data.accelerometerData.zAcceleration:F4}," +
                $"{data.gpsData.Latitude:F6}," +
                $"{data.gpsData.Longitude:F6}," +
                $"{data.gpsData.Altitude:F2}," +
                $"{data.gpsData.Speed:F2}," +
                $"{data.gpsData.Course:F2}," +
                $"{data.gpsData.SatelliteCount}," +
                $"\"{data.gpsData.FixType}\"," +  // Usa aspas para evitar erro com vírgulas
                $"{data.gpsData.GPSTimestamp:yyyy-MM-dd HH:mm:ss.fff}\n";

            // Escrita thread-safe
            lock (_lock)
            {
                File.AppendAllText(_filePath, line, Encoding.UTF8);
            }
        }
    }
}
