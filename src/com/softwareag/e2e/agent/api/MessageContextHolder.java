package com.softwareag.e2e.agent.api;

import java.util.HashMap;

import org.apache.skywalking.apm.agent.core.context.ContextCarrier;
import org.apache.skywalking.apm.agent.core.context.ContextManager;

import com.softwareag.pg.rest.RestMessageContext;

public class MessageContextHolder<T> {

	private HashMap<String, T> _refs = new HashMap<String, T>();
	
	public static MessageContextHolder<RestMessageContext> def = new MessageContextHolder<RestMessageContext>();
	
	
	public T get() {
	
		System.out.println("-------------- Getting context for " + getCurrentId());
		
		return _refs.get(getCurrentId());
	}
	
	public void set(T object) {
		
		System.out.println("-------------- Setting context for " + getCurrentId());

		_refs.put(getCurrentId(), object);
	}
	
	public T remove() {
		
		System.out.println("-------------- Removing context for " + getCurrentId());

		return _refs.remove(getCurrentId());
	}
	
	public String getCurrentId() {
		return ContextManager.getGlobalTraceId();
	}
}
