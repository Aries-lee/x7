package x7.core.web;

import x7.core.util.StringUtil;

public class TokenedRo implements Tokened{

	private String passportId;//groupId
	private String token;//
	private String device;// WX | ANDROID | IOS | PC
	public long getPassportId() {
		if (StringUtil.isNullOrEmpty(passportId))
			return 0;
		return Long.valueOf(passportId);
	}
	public void setPassportId(String passportId) {
		this.passportId = passportId;
	}
	public String getPassportName(){
		return this.passportId;
	}
	public String getToken() {
		return token;
	}
	public void setToken(String token) {
		this.token = token;
	}
	public String getDevice() {
		return device;
	}
	public void setDevice(String device) {
		this.device = device;
	}
	@Override
	public String toString() {
		return "BaseRo [passportId=" + passportId + ", token=" + token + ", device=" + device + "]";
	}
}
