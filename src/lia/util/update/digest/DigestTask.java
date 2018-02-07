/*
 * Created on Sep 27, 2010
 */
package lia.util.update.digest;

import java.io.File;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.Callable;

import lia.util.Utils;
import lia.util.update.UpdaterUtils;

/**
 * 
 *
 * @author ramiro
 */
class DigestTask implements Callable<DigestResult>{

    final File file;
    final String digestAlgo;
    final MessageDigest msgDigest;
    
    DigestTask(final File file, final String digestAlgo) throws NoSuchAlgorithmException {
        this.file = file;
        this.digestAlgo = digestAlgo;
        this.msgDigest = MessageDigest.getInstance(digestAlgo);
    }

    @Override
    public DigestResult call() throws Exception {
      final long sTime = Utils.nanoNow();
      final byte[] digest = UpdaterUtils.computeDigest(file, msgDigest);
      return new DigestResult(file, digest, file.length(), file.lastModified(), Utils.nanoNow() - sTime);
    }

}
