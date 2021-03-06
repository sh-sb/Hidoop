package ordo;

import java.net.MalformedURLException;

import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.util.Arrays;

import application.MyMapReduce;
import formats.Format;
import formats.Format.Type;
import formats.FormatReader;
import formats.FormatWriter;
import formats.KVFormat;
import formats.LineFormat;
import hdfs.HdfsClient;
import java.io.*;

public class HidoopClient {
	
	private static void usage() {
		System.out.println("Utilisation : java HidoopClient fichier format");
	}

	private static void recupURL(String[] urls) {
		File file = new File("../config/config_hidoop.cfg");
		int cpt = 0;
		  
		BufferedReader br;
		try {
			br = new BufferedReader(new FileReader(file));
			String st; 
			while ((st = br.readLine()) != null)
				urls[cpt] = st;
				cpt++;
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void main (String args[]) throws RemoteException {
		// Formats de fichiers utilisables
		String[] formats = {"line","kv"};
		
		// Nom du fichier écrit sur HDFS via HDFSClient 
		String hdfsFname;
		
		// Nom du fichier traité local
		String localFSDestFname;
		
		// Fichiers source / destination
		FormatReader reader;
		FormatWriter writer;
		
		// nombre de machines contenues dans le cluster
		int nbCluster;
		
		// informations de format de fichier
		String fname;
		Type ft;
		
		// liste des url correspondant aux démons du cluster
		String urlDaemon[];
		
		// liste des références aux démons du cluster dans le registre
		Daemon listeDaemon[];
		
		try {
			if (args.length < 2) {
				usage();
				System.exit(1);
			} else {
				if (!Arrays.asList(formats).contains(args[1])) {
					usage();
					System.exit(1);
				}
			}
			
			// Informations sur les fichiers manipulés
			hdfsFname = args[0];
			ft = Format.Type.KV;
			localFSDestFname = hdfsFname + "-res";
			
			// Récupérer le format de fichier indiqué en argument
			if (args[1].equals("line")) {
				ft = Format.Type.LINE;
				reader = new LineFormat(fname);
				writer = new LineFormat("");
				
			} else {
				ft = Format.Type.KV;
				reader = new KVFormat(fname);
				writer = new KVFormat("")
			}
						
			// informations sur le cluster
			nbCluster = 5;
			
			urlDaemon = new String[nbCluster];			
			recupURL(urlDaemon);

			// récupérer les références des objets Daemon distants
			// à l'aide des url (déjà connues)
			listeDaemon = new Daemon[nbCluster];
			
			for (int i = 0 ; i < nbCluster ; i++) { 
				listeDaemon[i]=(Daemon) Naming.lookup(urlDaemon[i]);
			}
			
			// création et définition des attributs de l'objet Job
			// on donne la liste des références aux Daemons à l'objet Job
			Job job = new Job(listeDaemon);
			
			// indiquer à job le nom et format du fichier à traiter
			job.setInputFname(hdfsFname);
			job.setInputFormat(ft);
			
			// création de l'objet MapReduce
			MyMapReduce mr = new MyMapReduce();
			
			// lancement des tâches
			job.startJob(mr);
			
			// attendre que toutes les tâches soient terminées
			while(!job.traitementFini());
			
			// récupérer le fichier traité via HDFS
			HdfsClient.HdfsRead(hdfsFname, localFSDestFname);
			
			// appliquer reduce sur le résultat
			// reader : format kv ; writer : format kv
			mr.reduce(reader, writer);
						
		} catch (RemoteException e) {
			e.printStackTrace();
			
		} catch (MalformedURLException e) {
			e.printStackTrace();
			
		} catch (NotBoundException e) {
			e.printStackTrace();
		}
	}

}
