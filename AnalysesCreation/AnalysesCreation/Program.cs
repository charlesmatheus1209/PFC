using OSIsoft.AF;
using OSIsoft.AF.Analysis;
using OSIsoft.AF.Asset;
using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Threading.Tasks;

namespace AnalysesCreation
{
    internal class Program
    {
        static void Main(string[] args)
        {
            PISystem piSystem = new PISystems()["DCCPMSSRV001"];

            piSystem.Connect();

            AFDatabase db = piSystem.Databases["VSB_PIMS"];
            AFElementTemplate template = db.ElementTemplates["ALARMES_PONTES"];
            AFAnalysisTemplate analysis = template.AnalysisTemplates["Alarme Acionado"];

            var mainText = "Alarme_???_?:= if BadVal('Alarme ???.?') then \r\n     \"\"\r\nelse \r\n    if('Alarme ???.?' = true) then\r\n          'Alarme ???.?|Descricao'\r\n     else \r\n          \"\"  \r\n";
            //var mainText = "Alarme_???_?:= if BadVal('Alarme ???.?') then \r\n     if('Alarme ???.?' = true) then\r\n          'Alarme ???.?|Descricao'\r\n     else \r\n          \"\" \r\nelse \r\n     \"\"";
            string realText = "";
            for(int i = 178; i <= 206; i++)
            {
                for(int j = 0; j <= 7; j++)
                {
                    var line = mainText;
                    realText += line.Replace("???", i.ToString()).Replace("?", j.ToString());
                    realText += ";";
                }
            }
            analysis.AnalysisRule.ConfigString = realText;

            db.CheckIn();


            using(StreamWriter sr = new StreamWriter("Resultado.txt"))
            {
                sr.WriteLine("Finalizado");
            }
        }
    }
}
