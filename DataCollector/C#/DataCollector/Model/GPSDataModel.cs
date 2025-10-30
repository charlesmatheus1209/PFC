using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace DataCollector.Model
{
    public class GPSDataModel
    {
        public DateTimeOffset GPSTimestamp { get; set; }
        public double Latitude { get; set; } = 0;
        public double Longitude { get; set; } = 0;
        public double Altitude { get; set; } = 0;
        public double Speed { get; set; } = 0;
        public double Course { get; set; } = 0;
        public int SatelliteCount { get; set; } = 0;
        public string FixType { get; set; } = "";
    }
}
