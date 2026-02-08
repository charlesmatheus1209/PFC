using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace DataCollector.Model
{
    public class DataModel
    {
        public AccelerometerDataModel accelerometerData { get; set; }
        public GPSDataModel gpsData { get; set; }
    }
}
