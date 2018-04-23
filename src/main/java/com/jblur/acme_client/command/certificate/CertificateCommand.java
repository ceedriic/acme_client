package com.jblur.acme_client.command.certificate;

import com.google.gson.reflect.TypeToken;
import com.jblur.acme_client.IOManager;
import com.jblur.acme_client.Parameters;
import com.jblur.acme_client.command.ACMECommand;
import com.jblur.acme_client.command.AccountKeyNotFoundException;
import com.jblur.acme_client.manager.CertificateManager;
import org.shredzone.acme4j.Certificate;
import org.shredzone.acme4j.exception.AcmeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.security.cert.CertificateEncodingException;
import java.util.LinkedList;
import java.util.List;

abstract class CertificateCommand extends ACMECommand {

    private static final Logger LOG = LoggerFactory.getLogger(CertificateCommand.class);

    private static Type listOfCertificateLocationObject = new TypeToken<List<String>>(){}.getType();

    final String CERTIFICATE_FILE_PATH;

    CertificateCommand(Parameters parameters) throws AccountKeyNotFoundException {
        super(parameters);
        CERTIFICATE_FILE_PATH = Paths.get(getParameters().getWorkDir(), Parameters.CERTIFICATE_URI_LIST).toString();
    }

    boolean writeCertificate(CertificateManager certificateManagement, String suffix) {
        boolean error = false;
        try {
            IOManager.writeX509Certificate(certificateManagement.downloadCertificate(),
                    Paths.get(getParameters().getCertDir(), "cert" + suffix + ".pem").toString());
            IOManager.writeX509CertificateChain(certificateManagement.downloadCertificateChain(),
                    Paths.get(getParameters().getCertDir(), "chain" + suffix + ".pem").toString());
            IOManager.writeX509CertificateChain(certificateManagement.downloadFullChainCertificate(),
                    Paths.get(getParameters().getCertDir(), "fullchain" + suffix + ".pem").toString());
        } catch (IOException e) {
            LOG.error("Cannot write certificate into dir: " + getParameters().getCertDir(), e);
            error = true;
        } catch (CertificateEncodingException e) {
            LOG.error("Cannot write certificate. Encoding exception.", e);
            error = true;
        } catch (AcmeException e) {
            LOG.error("Cannot download certificate: " + certificateManagement.getCertificate().getLocation(), e);
            error = true;
        }
        return !error;
    }


    boolean writeCertificateList(List<Certificate> certificateList) {
        try {
            List<String> certificateLocationList = new LinkedList<>();
            for(Certificate certificate : certificateList){
                certificateLocationList.add(certificate.getLocation().toString());
            }
            IOManager.writeString(CERTIFICATE_FILE_PATH,
                    getGson().toJson(certificateLocationList, listOfCertificateLocationObject));
        } catch (IOException e) {
            LOG.error("Cannot write certificate list to file: " + Paths.get(getParameters().getWorkDir(),
                    Parameters.CERTIFICATE_URI_LIST).toString() + "\n Please check permissions of the file.", e);
            return false;
        }
        return true;
    }

    List<Certificate> getNotExpiredCertificates() {
        List<Certificate> oldCertificateList = new LinkedList<>();

        if (!IOManager.isFileExists(CERTIFICATE_FILE_PATH)) {
            return null;
        }

        List<String> certificateLocationList;
        try {
            certificateLocationList = getGson().fromJson(
                    IOManager.readString(CERTIFICATE_FILE_PATH),
                    listOfCertificateLocationObject);
        } catch (Exception e) {
            LOG.warn("Your file cannot be read. It has a bad structure", e);
            return null;
        }

        for(String certificateLocation : certificateLocationList){
            try {
                oldCertificateList.add(Certificate.bind(getSession(), new URL(certificateLocation)));
            } catch (MalformedURLException e) {
                LOG.warn("URL isn't correct: "+certificateLocation, e);
            } catch (Exception e){
                LOG.warn("Cannot retrieve certificate: "+certificateLocation, e);
            }
        }

        List<Certificate> certificateList = new LinkedList<>();

        for (Certificate certificate : oldCertificateList) {
            try {
                if (certificate.download().getNotAfter().getTime() > System.currentTimeMillis()) {
                    certificateList.add(certificate);
                }
            } catch (AcmeException e) {
                LOG.warn("Cannot download a certificate: " + certificate.getLocation(), e);
                certificateList.add(certificate);
            } catch (NullPointerException e){
                LOG.warn("Found NULL certificate in the file " +
                        CERTIFICATE_FILE_PATH+". Remove NULL certificate.", e);
            } catch (Exception e) {
                LOG.warn("Certificate "+certificate.getLocation().toString()+" cannot be rebinded. " +
                        "Please check internet connectivity or certificate existence.", e);
                certificateList.add(certificate);
            }
        }

        return certificateList;
    }

}
