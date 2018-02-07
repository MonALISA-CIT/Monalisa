/* JAAS login configuration file for Reggie */

com.sun.jini.Reggie {
    com.sun.security.auth.module.KeyStoreLoginModule required
	keyStoreAlias="local_lus"
	keyStoreURL="file:${jini.ser.reggie.home}/certs/lus.keystore"
	keyStorePasswordURL="file:${jini.ser.reggie.home}/certs/lus.password";
};

