package com.gotye.bibo.util;

import java.io.File;
import java.io.FileFilter;

public class FileFilterTest implements FileFilter {

	String[] condition = null;

	public FileFilterTest(String[] condition) {
		this.condition = condition;
	}

	@Override
	public boolean accept(File pathname) {
		// TODO Auto-generated method stub
		if (condition == null || condition.length == 0)
			return false;

		String filename = pathname.getName();
		String ext = filename;
		int pos = filename.lastIndexOf('.');
		if (pos == -1)
			return false;

		ext = filename.substring(pos + 1, filename.length());
		for (int i = 0; i < condition.length; i++) {
			if (ext.equals(condition[i]))
				return true;
		}

		return false;
	}
}