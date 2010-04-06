package org.sakaiproject.user.impl;

import java.io.FileInputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;

public class ReEncryptPasswords {

	/**
	 * Command line utility to re-encrypt all passwords in the database that use unsalted MD5.
	 * It should be run on the command line in the sakai.home folder with both the kernel-impl
	 * and SQL drvier jars on the classpath. 
	 * @param args
	 * @throws SQLException 
	 */
	public static void main(String[] args) throws Exception {
		
		Properties props = new Properties();
		props.load(new FileInputStream(System.getProperty("sakai.properties", "sakai.properties")));
		String location = null;
		try {
			location = System.getProperty("local.properties", "local.properties");
			props.load(new FileInputStream(location));
		} catch (Exception e) {
			System.out.println("Didn't load local.properties: "+ location);
		}
		try {
			location = System.getProperty("security.properties", "security.properties"); 
			props.load(new FileInputStream(location));
		} catch (Exception e) {
			System.out.println("Didn't load security.properties: "+ location);
		}
		
		String url, username, password, driver;
		
		url = props.getProperty("url@javax.sql.BaseDataSource");
		if (url == null) {
			throw new IllegalStateException("url can't be null");
		}
		username = props.getProperty("username@javax.sql.BaseDataSource");
		if (username == null) {
			throw new IllegalStateException("username can't be null");
		}
		password = props.getProperty("password@javax.sql.BaseDataSource");
		if (password == null) {
			throw new IllegalStateException("password can't be null");
		}
		driver = props.getProperty("driverClassName@javax.sql.BaseDataSource");
		if (driver == null) {
			throw new IllegalStateException("driver can't be null");
		}

		Class.forName(driver);
		
		PasswordService pwdService = new PasswordService();
		
		Connection conn = DriverManager.getConnection(url, username, password);
		conn.setAutoCommit(false);
		PreparedStatement usersSt = conn.prepareStatement("SELECT USER_ID, PW FROM SAKAI_USER FOR UPDATE", ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_UPDATABLE);
		ResultSet usersRs = usersSt.executeQuery();
		
		int total = 0, updated = 0;
		while (usersRs.next()) {
			total++;
			String currentPw = usersRs.getString("PW");
			String newPw = null;
			if (currentPw.length() == 20) {
				newPw = PasswordService.MD5TRUNC_SALT_SHA256+ pwdService.encrypt(currentPw);
			} else if (currentPw.length() == 24) {
				newPw = PasswordService.MD5_SALT_SHA256+ pwdService.encrypt(currentPw);
			}
			if (newPw != null) {
				usersRs.updateString("PW", newPw);
				usersRs.updateRow();
				updated++;
			}
		}
		conn.commit();
		System.out.println(" Users processed: "+ total+ " updated: "+ updated);
	}

}
