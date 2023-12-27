package com.github.xdamah.param;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import com.github.xdamah.magic.controller.DamahController;

import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.CookieParameter;
import io.swagger.v3.oas.models.parameters.HeaderParameter;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.parameters.PathParameter;
import io.swagger.v3.oas.models.parameters.QueryParameter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

public abstract class BaseParameterProcessor {
	
	
	
	public BaseParameterProcessor(String path, DamahController magicController) {
		super();
		
		this.path = path;
		this.magicController=magicController;
		
	}
	
	private String path; 
	private  DamahController magicController; 
	

	
	
	public Object process(HttpServletRequest request,  Parameter operationParameter) throws IllegalAccessException, InvocationTargetException
	{
		Object ret=null;
		String ref = operationParameter.get$ref();
		if(ref!=null)
		{
			operationParameter=this.magicController.getFromRef(ref);
			if(operationParameter==null)
			{
				//already logged warning
				//log again no harm
				//lets just skip
				return null;
			}
		}
		
		
		//improve this and dont assume its all parameters
		String operationParameterName = operationParameter.getName();
		
		Schema operationParameterSchema = operationParameter.getSchema();
		if(operationParameterSchema!=null)
		{
			String type = operationParameterSchema.getType();
			if(type!=null && type.equals("array"))
			{
				if(operationParameter instanceof QueryParameter)
				{
					String[] src=request.getParameterValues(operationParameterName);
					//System.out.println("got src="+Arrays.toString(src));
					if(src!=null)
					{
						List<Object> list= Arrays.asList(src);
						ret = returnAndUse(operationParameterName, list);
					}
				}
				else if(operationParameter instanceof HeaderParameter)
				{
					Enumeration<String> headersEnum = request.getHeaders(operationParameterName);
					if(headersEnum!=null)
					{
						List<Object> headersList= new ArrayList<>();
						while(headersEnum.hasMoreElements())
						{
							headersList.add(headersEnum.nextElement());
						}
						ret = returnAndUse(operationParameterName, headersList);
						
					}
					
				}
				else if(operationParameter instanceof CookieParameter)
				{
					String vals=getCookieVal(request, operationParameterName);
					if(vals!=null)
					{
						String[] splits = vals.split(",");
						List<Object> valList= new ArrayList<>();
						for (String split : splits) {
							valList.add(split);
						}
						ret = returnAndUse(operationParameterName, valList);
					}
					
				}
				else if(operationParameter instanceof PathParameter)
				{
					String vals = getPathVal(request, operationParameterName);
					if(vals!=null)
					{
						String[] splits = vals.split(",");
						List<Object> valList= new ArrayList<>();
						for (String split : splits) {
							valList.add(split);
						}
						ret = returnAndUse(operationParameterName, valList);
					}
				}
				
				
			}
			else
			{
				if(operationParameter instanceof QueryParameter)
				{
					String src=request.getParameter(operationParameterName);
					System.out.println("got src="+src);
					ret = returnAndUse(operationParameterName, src);
				}
				else if(operationParameter instanceof HeaderParameter)
				{
					String header = request.getHeader(operationParameterName);
					if(header!=null)
					{
						ret = returnAndUse(operationParameterName, header);
					}
				}
				else if(operationParameter instanceof CookieParameter)
				{
					String val=getCookieVal(request, operationParameterName);
					if(val!=null)
					{
						ret = returnAndUse(operationParameterName, val);
					}
					
				}
				else if(operationParameter instanceof PathParameter)
				{
					
					
					String val = getPathVal(request, operationParameterName);
					if(val!=null)
					{
						ret = returnAndUse(operationParameterName, val);
					}
					
					
				}
			}
				
			
		}
		return ret;
	}

	protected abstract Object returnAndUse(String operationParameterName, String src)
			throws IllegalAccessException, InvocationTargetException;

	protected abstract List<Object> returnAndUse(String operationParameterName, List<Object> list)
			throws IllegalAccessException, InvocationTargetException;
	
	private String getCookieVal(HttpServletRequest request, String operationParameterName) {
		String val=null;
		Cookie[] cookies = request.getCookies();
		if(cookies!=null)
		{
			for (Cookie cookie : cookies) {
				String name = cookie.getName();
				if(name.equals(operationParameterName))
				{
					val = cookie.getValue();
					
					
				}
				
			}
		}
		return val;
	}
	private String getPathVal(HttpServletRequest request, String operationParameterName) {
		String val=null;
		String requestURI = request.getRequestURI();
		String searchFor="{"+operationParameterName+"}";
		String[] splitPathItemKey = this.path.split("/");
		
		
		String[] splitRequestUri = requestURI.split("/");
		for (int i = 0; i < splitPathItemKey.length; i++) {
			if(splitPathItemKey[i].equals(searchFor))
			{
				//here is where we expect it
				if(i<splitRequestUri.length)
				{
					val=splitRequestUri[i];
				}
				else
				{
					
					//the path is not there in actual
					//dp nothing
				}
				break;
			}
		}
		return val;
	}


}
