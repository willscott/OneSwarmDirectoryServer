OneSwarm Directory Service
======

Usage
------
Run the java code (The main entry point is in OSDirectoryServer).
If it is the first time, the server will generate a signing key,
and will sign all listings with that key.  The key pair will be stored
in the file "key.store", and the public certification will be stored
in "directory.cert".  Clients should be given the certificate to
validate the authenticity of responses they receive from the directory.

If the file "knownPartners.txt" is present, it will be used to
allow multiple instances of the service to coordinate as a single entity.
The file should contain one entry per line of the address (host:port) of
the other instances.  When updates are given an instance, it will
relay those state changes to the other instances.

Inprogress:
------
 * Updates should be encrypted with the public key, so that an attacker
   gaining access of the directory server can't see payloads.

 * Syncronization between instances should be bundled, to lower load.

 * Additional verification of registrations should occur:
    * Limit registrations allowed to each IP.
    * Ensure a service is running at the requested service.
    * Ensure the asserted bandwidth / location / policy are reasonable.
