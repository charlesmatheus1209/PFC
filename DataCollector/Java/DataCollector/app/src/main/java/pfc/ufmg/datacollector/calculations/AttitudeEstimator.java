package pfc.ufmg.datacollector.calculations;

import android.content.Context;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe para estimação de atitude (phi, theta, psi) a partir de dados de acelerômetro e GPS
 * Conversão do algoritmo MATLAB attitude_estimation_v7_gimbal_lock
 */
public class AttitudeEstimator {

    // Constantes
    private static final double G_EARTH = 9.8; // Aceleração da gravidade
    private static final int FILTER_ORDER = 63;
    private static final double FC = 0.3; // Frequência de corte
    private static final double FS = 20.0; // Frequência de amostragem
    private static final double GPS_FILTER_DELAY = 1.5;
    private static final double VLOW = 15.0; // Velocidade mínima GPS (km/h)
    private static final double AHIGH = 0.12; // Limite superior de aceleração (g)
    private static final double ALOW = 0.08; // Limite inferior de desvio padrão
    private static final double MAX_DURATION_EVENT = 10.0; // Duração máxima do evento (s)
    private static final int NACCEL_GRAV = 200;

    // Resultados
    private double phiA = 1234;
    private double thetaA = 1234;
    private double psiA = 1234;

    // Buffers
    private double[][] filterBuffer;
    private double[][] accelBufferGrav;
    private double[][] accelBuffer;
    private double[][] gpsBuffer;
    private double[] filterCoeff;

    // Contadores
    private int accelBufferGravCont = 0;
    private int accelBufferCont = 0;
    private int gpsBufferCont = 0;
    private int gpsNsamp = 0;

    // Estado
    private boolean event = false;
    private double maxAccelDev = 0;
    private int posMaxAccelDev = 1;
    private double[] gA = {0, 0, 1}; // Vetor gravidade normalizado

    // Dados CSV
    private List<CsvData> dataList;

    /**
     * Classe para armazenar dados do CSV
     */
    private static class CsvData {
        int contreg;
        double eixox;
        double eixoy;
        double eixoz;
        int gpsFix;
        double gpsSpeed;
        double gpsDirection;
        double gpsAlt;
        double gpsRtc;

        CsvData(int contreg, double eixox, double eixoy, double eixoz,
                int gpsFix, double gpsSpeed, double gpsDirection,
                double gpsAlt, double gpsRtc) {
            this.contreg = contreg;
            this.eixox = eixox;
            this.eixoy = eixoy;
            this.eixoz = eixoz;
            this.gpsFix = gpsFix;
            this.gpsSpeed = gpsSpeed;
            this.gpsDirection = gpsDirection;
            this.gpsAlt = gpsAlt;
            this.gpsRtc = gpsRtc;
        }
    }

    /**
     * Classe para retornar resultados
     */
    public static class AttitudeResult {
        public double phiDegrees;
        public double thetaDegrees;
        public double psiDegrees;
        public double phiRadians;
        public double thetaRadians;
        public double psiRadians;

        AttitudeResult(double phi, double theta, double psi) {
            this.phiRadians = phi;
            this.thetaRadians = theta;
            this.psiRadians = psi;
            this.phiDegrees = Math.toDegrees(phi);
            this.thetaDegrees = Math.toDegrees(theta);
            this.psiDegrees = Math.toDegrees(psi);
        }
    }

    /**
     * Construtor
     */
    public AttitudeEstimator() {
        initializeBuffers();
        generateFilterCoefficients();
    }

    /**
     * Inicializa buffers
     */
    private void initializeBuffers() {
        filterBuffer = new double[FILTER_ORDER + 1][3];
        accelBufferGrav = new double[NACCEL_GRAV][3];

        int naccel = (int) Math.ceil(MAX_DURATION_EVENT / (1.0 / FS));
        accelBuffer = new double[naccel][3];

        int ngps = (int) Math.ceil((GPS_FILTER_DELAY + MAX_DURATION_EVENT) / 1.0) + 1;
        gpsBuffer = new double[ngps][5];
    }

    /**
     * Gera coeficientes do filtro FIR
     * Implementação simplificada de fir1 do MATLAB
     */
    private void generateFilterCoefficients() {
        filterCoeff = new double[FILTER_ORDER + 1];
        double wc = FC / (FS / 2.0);

        for (int i = 0; i <= FILTER_ORDER; i++) {
            int n = i - FILTER_ORDER / 2;
            if (n == 0) {
                filterCoeff[i] = wc;
            } else {
                filterCoeff[i] = Math.sin(Math.PI * wc * n) / (Math.PI * n);
            }

            // Janela de Hamming
            filterCoeff[i] *= 0.54 - 0.46 * Math.cos(2.0 * Math.PI * i / FILTER_ORDER);
        }

        // Normaliza
        double sum = 0;
        for (double coeff : filterCoeff) {
            sum += coeff;
        }
        for (int i = 0; i < filterCoeff.length; i++) {
            filterCoeff[i] /= sum;
        }
    }

    /**
     * Lê arquivo CSV
     */
    private void readCsv(Context context, String filename) throws IOException {
        dataList = new ArrayList<>();
        InputStream inputStream = context.getAssets().open(filename);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

        String line;
        boolean firstLine = true;

        while ((line = reader.readLine()) != null) {
            if (firstLine) {
                firstLine = false;
                continue; // Pula cabeçalho
            }

            String[] parts = line.split(",");
            if (parts.length >= 9) {
                try {
                    int contreg = Integer.parseInt(parts[0].trim());
                    double eixox = Double.parseDouble(parts[1].trim());
                    double eixoy = Double.parseDouble(parts[2].trim());
                    double eixoz = Double.parseDouble(parts[3].trim());
                    int gpsFix = Integer.parseInt(parts[4].trim());
                    double gpsSpeed = Double.parseDouble(parts[5].trim());
                    double gpsDirection = Double.parseDouble(parts[6].trim());
                    double gpsAlt = Double.parseDouble(parts[7].trim());
                    double gpsRtc = Double.parseDouble(parts[8].trim());

                    dataList.add(new CsvData(contreg, eixox, eixoy, eixoz,
                            gpsFix, gpsSpeed, gpsDirection, gpsAlt, gpsRtc));
                } catch (NumberFormatException e) {
                    // Ignora linhas com erro
                }
            }
        }

        reader.close();
    }

    /**
     * Executa o algoritmo de estimação
     */
    public AttitudeResult estimate(Context context, String filename) throws IOException {
        readCsv(context, filename);
        processData();
        return new AttitudeResult(phiA, thetaA, psiA);
    }

    /**
     * Processa os dados
     */
    private void processData() {
        int gpsFilterDelaySamples = (int) Math.ceil(GPS_FILTER_DELAY / (1.0 / FS));

        for (int n = 0; n < dataList.size(); n++) {
            CsvData data = dataList.get(n);

            // Atualiza buffer do filtro
            shiftBuffer(filterBuffer);
            filterBuffer[filterBuffer.length - 1][0] = data.eixox / G_EARTH;
            filterBuffer[filterBuffer.length - 1][1] = data.eixoy / G_EARTH;
            filterBuffer[filterBuffer.length - 1][2] = data.eixoz / G_EARTH;

            // Aguarda inicialização do filtro
            if (n <= FILTER_ORDER) {
                continue;
            }

            // Filtragem passa-baixas
            double[] accelF = applyFilter();

            // Estimação inicial de phi e theta
            if (phiA == 1234 || thetaA == 1234) {
                double accelDev = calculateStdDev();

                if (accelDev < ALOW) {
                    accelBufferGravCont++;
                    System.arraycopy(accelF, 0, accelBufferGrav[accelBufferGravCont - 1], 0, 3);
                } else {
                    accelBufferGravCont = 0;
                }

                if (accelBufferGravCont == NACCEL_GRAV) {
                    estimateRollPitch();
                }
            }

            // Detecção de eventos e estimação de yaw
            if (phiA != 1234 && thetaA != 1234) {
                double[] accelDev = calculateAccelDeviation(accelF);
                double accelDevMag = Math.sqrt(accelDev[0] * accelDev[0] +
                        accelDev[1] * accelDev[1] +
                        accelDev[2] * accelDev[2]);

                if (accelDevMag > AHIGH) {
                    handleAccelEvent(accelF, accelDevMag);
                } else {
                    if (event) {
                        estimateYaw();
                        if (gpsBufferCont > 0 && gpsBuffer[gpsBufferCont - 1][2] == 1234) {
                            gpsBufferCont = 0;
                        }
                    }
                    event = false;
                    maxAccelDev = 0;
                    posMaxAccelDev = 1;
                }
            }

            // Coleta dados GPS
            int m = n - gpsFilterDelaySamples;
            if (m >= 0 && m < dataList.size()) {
                processGpsData(m);
            }

            gpsNsamp++;
        }
    }

    /**
     * Aplica filtro FIR
     */
    private double[] applyFilter() {
        double[] result = new double[3];
        for (int i = 0; i < filterBuffer.length; i++) {
            result[0] += filterCoeff[i] * filterBuffer[i][0];
            result[1] += filterCoeff[i] * filterBuffer[i][1];
            result[2] += filterCoeff[i] * filterBuffer[i][2];
        }
        return result;
    }

    /**
     * Calcula desvio padrão da aceleração
     */
    private double calculateStdDev() {
        double[] mean = new double[3];
        for (int i = 0; i < filterBuffer.length; i++) {
            mean[0] += filterBuffer[i][0];
            mean[1] += filterBuffer[i][1];
            mean[2] += filterBuffer[i][2];
        }
        mean[0] /= filterBuffer.length;
        mean[1] /= filterBuffer.length;
        mean[2] /= filterBuffer.length;

        double[] var = new double[3];
        for (int i = 0; i < filterBuffer.length; i++) {
            var[0] += Math.pow(filterBuffer[i][0] - mean[0], 2);
            var[1] += Math.pow(filterBuffer[i][1] - mean[1], 2);
            var[2] += Math.pow(filterBuffer[i][2] - mean[2], 2);
        }
        var[0] = Math.sqrt(var[0] / filterBuffer.length);
        var[1] = Math.sqrt(var[1] / filterBuffer.length);
        var[2] = Math.sqrt(var[2] / filterBuffer.length);

        return Math.sqrt(var[0] * var[0] + var[1] * var[1] + var[2] * var[2]);
    }

    /**
     * Calcula desvio de aceleração
     */
    private double[] calculateAccelDeviation(double[] accelF) {
        return new double[]{
                accelF[0] - gA[0],
                accelF[1] - gA[1],
                accelF[2] - gA[2]
        };
    }

    /**
     * Trata evento de aceleração
     */
    private void handleAccelEvent(double[] accelF, double accelDevMag) {
        if (!event) {
            event = true;
            accelBufferCont = 1;
        } else {
            accelBufferCont++;
            if (accelBufferCont > accelBuffer.length) {
                accelBufferCont = accelBuffer.length;
            }
        }

        shiftBuffer(accelBuffer);
        System.arraycopy(accelF, 0, accelBuffer[accelBuffer.length - 1], 0, 3);

        if (accelDevMag > maxAccelDev) {
            maxAccelDev = accelDevMag;
            posMaxAccelDev = accelBufferCont;
        }
    }

    /**
     * Estima roll e pitch
     */
    private void estimateRollPitch() {
        double[] mean = new double[3];
        for (int i = 0; i < accelBufferGrav.length; i++) {
            mean[0] += accelBufferGrav[i][0];
            mean[1] += accelBufferGrav[i][1];
            mean[2] += accelBufferGrav[i][2];
        }
        mean[0] /= accelBufferGrav.length;
        mean[1] /= accelBufferGrav.length;
        mean[2] /= accelBufferGrav.length;

        double norm = Math.sqrt(mean[0] * mean[0] + mean[1] * mean[1] + mean[2] * mean[2]);
        gA[0] = mean[0] / norm;
        gA[1] = mean[1] / norm;
        gA[2] = mean[2] / norm;

        phiA = Math.atan2(-gA[1], -gA[2]);
        thetaA = Math.asin(gA[0]);

        System.out.println("phi_a = " + Math.toDegrees(phiA) + " deg. theta_a = " + Math.toDegrees(thetaA));
    }

    /**
     * Estima yaw
     */
    private void estimateYaw() {
        if (gpsBufferCont == 0) {
            return;
        }

        // Prepara dados GPS válidos
        int endGps = gpsBufferCont;
        int startGps = 0;

        for (int k = endGps - 1; k >= 0; k--) {
            if (gpsBuffer[k][2] != 1234) {
                startGps = k;
                break;
            }
        }

        if (endGps - startGps < 2) {
            return;
        }

        // Calcula aceleração via GPS
        List<double[]> accelGps = new ArrayList<>();
        List<Double> psiV = new ArrayList<>();

        for (int i = startGps; i < endGps - 1; i++) {
            double dt = gpsBuffer[i + 1][0] - gpsBuffer[i][0];
            if (dt == 0) continue;

            double v1x = gpsBuffer[i][1] * 1000.0 / 3600.0 * Math.cos(Math.toRadians(gpsBuffer[i][2]));
            double v1y = gpsBuffer[i][1] * 1000.0 / 3600.0 * Math.sin(Math.toRadians(gpsBuffer[i][2]));
            double v2x = gpsBuffer[i + 1][1] * 1000.0 / 3600.0 * Math.cos(Math.toRadians(gpsBuffer[i + 1][2]));
            double v2y = gpsBuffer[i + 1][1] * 1000.0 / 3600.0 * Math.sin(Math.toRadians(gpsBuffer[i + 1][2]));

            double ax = (v2x - v1x) / dt / G_EARTH;
            double ay = (v2y - v1y) / dt / G_EARTH;

            accelGps.add(new double[]{ax, ay, 0});
            psiV.add(Math.atan2(v1y, v1x));
        }

        if (accelGps.isEmpty()) {
            return;
        }

        // Matrizes de rotação
        double[][] rPhi = {
                {1, 0, 0},
                {0, Math.cos(phiA), Math.sin(phiA)},
                {0, -Math.sin(phiA), Math.cos(phiA)}
        };

        double[][] rTheta = {
                {Math.cos(thetaA), 0, -Math.sin(thetaA)},
                {0, 1, 0},
                {Math.sin(thetaA), 0, Math.cos(thetaA)}
        };

        List<Double> psiAList = new ArrayList<>();
        List<Double> residuoList = new ArrayList<>();

        for (int k = 0; k < accelGps.size(); k++) {
            int bufferIdx = accelBuffer.length - accelBufferCont + (int) gpsBuffer[startGps + k][4];
            if (bufferIdx < 0 || bufferIdx >= accelBuffer.length) continue;

            double[] am = accelBuffer[bufferIdx];
            double[] amMinusG = {am[0] - gA[0], am[1] - gA[1], am[2] - gA[2]};
            double[] w = multiplyMatrixVector(transpose(rTheta), multiplyMatrixVector(transpose(rPhi), amMinusG));

            double psi = psiV.get(k);
            double[][] rPsi = {
                    {Math.cos(psi), Math.sin(psi), 0},
                    {-Math.sin(psi), Math.cos(psi), 0},
                    {0, 0, 1}
            };

            double[] v = multiplyMatrixVector(rPsi, accelGps.get(k));
            double psiAEst = Math.atan2(v[1] * w[0] - v[0] * w[1], v[0] * w[0] + v[1] * w[1]);
            psiAList.add(psiAEst);

            double[][] rPsiA = {
                    {Math.cos(psiAEst), Math.sin(psiAEst), 0},
                    {-Math.sin(psiAEst), Math.cos(psiAEst), 0},
                    {0, 0, 1}
            };

            double[] predicted = multiplyMatrixVector(rPhi, multiplyMatrixVector(rTheta,
                    multiplyMatrixVector(rPsiA, multiplyMatrixVector(rPsi, accelGps.get(k)))));
            double[] residuo = {amMinusG[0] - predicted[0], amMinusG[1] - predicted[1], amMinusG[2] - predicted[2]};
            double residuoMag = Math.sqrt(residuo[0] * residuo[0] + residuo[1] * residuo[1] + residuo[2] * residuo[2]);
            residuoList.add(residuoMag);
        }

        if (psiAList.isEmpty()) {
            return;
        }

        double meanResiduo = residuoList.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double meanPsiA = psiAList.stream().mapToDouble(Double::doubleValue).average().orElse(0);

        if (accelGps.size() > 2 && meanResiduo < 0.1) {
            psiA = meanPsiA;
            System.out.println("psi_a = " + Math.toDegrees(psiA) + " deg. Residuo: " + meanResiduo +
                    " Num. pts. GPS: " + accelGps.size());
        }
    }

    /**
     * Processa dados GPS
     */
    private void processGpsData(int m) {
        CsvData data = dataList.get(m);

        if (data.gpsFix == 3 && data.gpsSpeed >= VLOW) {
            if (gpsBufferCont == 0) {
                gpsBuffer[0][0] = data.gpsRtc;
                gpsBuffer[0][1] = data.gpsSpeed;
                gpsBuffer[0][2] = data.gpsDirection;
                gpsBuffer[0][3] = data.gpsAlt;
                gpsBuffer[0][4] = 0;
                gpsNsamp = 0;
                gpsBufferCont = 1;
            } else if (data.gpsRtc != gpsBuffer[gpsBufferCont - 1][0]) {
                if (gpsBufferCont == gpsBuffer.length) {
                    shiftBuffer(gpsBuffer);
                    gpsBufferCont--;
                }
                gpsBuffer[gpsBufferCont][0] = data.gpsRtc;
                gpsBuffer[gpsBufferCont][1] = data.gpsSpeed;
                gpsBuffer[gpsBufferCont][2] = data.gpsDirection;
                gpsBuffer[gpsBufferCont][3] = data.gpsAlt;
                gpsBuffer[gpsBufferCont][4] = gpsNsamp;
                gpsNsamp = 0;
                gpsBufferCont++;
            }
        } else {
            if (!event) {
                gpsBufferCont = 0;
            } else if (gpsBufferCont > 0 && data.gpsRtc != gpsBuffer[gpsBufferCont - 1][0]) {
                if (gpsBufferCont == gpsBuffer.length) {
                    shiftBuffer(gpsBuffer);
                    gpsBufferCont--;
                }
                gpsBuffer[gpsBufferCont][0] = data.gpsRtc;
                gpsBuffer[gpsBufferCont][1] = 0;
                gpsBuffer[gpsBufferCont][2] = 1234;
                gpsBuffer[gpsBufferCont][3] = 1234;
                gpsBuffer[gpsBufferCont][4] = gpsNsamp;
                gpsNsamp = 0;
                gpsBufferCont++;
            }
        }
    }

    /**
     * Desloca buffer (remove primeira linha)
     */
    private void shiftBuffer(double[][] buffer) {
        for (int i = 0; i < buffer.length - 1; i++) {
            System.arraycopy(buffer[i + 1], 0, buffer[i], 0, buffer[i].length);
        }
    }

    /**
     * Multiplica matriz por vetor
     */
    private double[] multiplyMatrixVector(double[][] matrix, double[] vector) {
        double[] result = new double[matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < vector.length; j++) {
                result[i] += matrix[i][j] * vector[j];
            }
        }
        return result;
    }

    /**
     * Transpõe matriz
     */
    private double[][] transpose(double[][] matrix) {
        double[][] result = new double[matrix[0].length][matrix.length];
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[0].length; j++) {
                result[j][i] = matrix[i][j];
            }
        }
        return result;
    }
}