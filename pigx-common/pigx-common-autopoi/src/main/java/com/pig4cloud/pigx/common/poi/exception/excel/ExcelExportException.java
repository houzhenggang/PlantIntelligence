package com.pig4cloud.pigx.common.poi.exception.excel;

import com.pig4cloud.pigx.common.poi.exception.excel.enums.ExcelExportEnum;

/**
 * 导出异常
 *
 * @author: Luckly
 * @date: 2019-05-20
 * <p>
 */
public class ExcelExportException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private ExcelExportEnum type;

	public ExcelExportException() {
		super();
	}

	public ExcelExportException(ExcelExportEnum type) {
		super(type.getMsg());
		this.type = type;
	}

	public ExcelExportException(ExcelExportEnum type, Throwable cause) {
		super(type.getMsg(), cause);
	}

	public ExcelExportException(String message) {
		super(message);
	}

	public ExcelExportException(String message, ExcelExportEnum type) {
		super(message);
		this.type = type;
	}

	public ExcelExportEnum getType() {
		return type;
	}

	public void setType(ExcelExportEnum type) {
		this.type = type;
	}

}
