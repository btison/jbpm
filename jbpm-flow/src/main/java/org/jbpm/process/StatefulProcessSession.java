/*
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
*/

package org.jbpm.process;

import org.drools.core.SessionConfiguration;
import org.drools.core.base.MapGlobalResolver;
import org.drools.core.common.EndOperationListener;
import org.drools.core.common.InternalKnowledgeRuntime;
import org.drools.core.common.WorkingMemoryAction;
import org.drools.core.impl.AbstractRuntime;
import org.drools.core.time.TimerService;
import org.drools.core.time.TimerServiceFactory;
import org.jbpm.process.instance.InternalProcessRuntime;
import org.jbpm.process.instance.ProcessRuntimeImpl;
import org.kie.api.command.Command;
import org.kie.api.event.process.ProcessEventListener;
import org.kie.api.event.rule.AgendaEventListener;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.kie.api.runtime.Calendars;
import org.kie.api.runtime.Channel;
import org.kie.api.runtime.Environment;
import org.kie.api.runtime.Globals;
import org.kie.api.runtime.KieSessionConfiguration;
import org.kie.api.runtime.ObjectFilter;
import org.kie.api.runtime.process.ProcessInstance;
import org.kie.api.runtime.process.WorkItemHandler;
import org.kie.api.runtime.process.WorkItemManager;
import org.kie.api.runtime.rule.Agenda;
import org.kie.api.runtime.rule.AgendaFilter;
import org.kie.api.runtime.rule.EntryPoint;
import org.kie.api.runtime.rule.FactHandle;
import org.kie.api.runtime.rule.LiveQuery;
import org.kie.api.runtime.rule.QueryResults;
import org.kie.api.runtime.rule.ViewChangedEventListener;
import org.kie.api.time.SessionClock;
import org.kie.internal.KnowledgeBase;
import org.kie.internal.process.CorrelationAwareProcessRuntime;
import org.kie.internal.process.CorrelationKey;
import org.kie.internal.runtime.StatefulKnowledgeSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

public class StatefulProcessSession extends AbstractRuntime implements StatefulKnowledgeSession, InternalKnowledgeRuntime, CorrelationAwareProcessRuntime {

	private KnowledgeBase kbase;
	private InternalProcessRuntime processRuntime;
	private WorkItemManager workItemManager;
	private KieSessionConfiguration sessionConfiguration;
	private Environment environment;
	private TimerService timerService;
	protected Queue<WorkingMemoryAction> actionQueue;
	private Long id;
	private MapGlobalResolver globals = new MapGlobalResolver();
	
	public StatefulProcessSession(KnowledgeBase kbase, KieSessionConfiguration sessionConfiguration, Environment environment) {
		this.kbase = kbase;
		this.sessionConfiguration = sessionConfiguration;
		this.environment = environment;
		timerService = TimerServiceFactory.getTimerService((SessionConfiguration) sessionConfiguration);
		processRuntime = new ProcessRuntimeImpl(this);
		actionQueue = new LinkedList<WorkingMemoryAction>();
	}
	
	public void abortProcessInstance(long processInstanceId) {
		processRuntime.abortProcessInstance(processInstanceId);
	}

	public ProcessInstance getProcessInstance(long processInstanceId) {
		return processRuntime.getProcessInstance(processInstanceId);
	}

	public ProcessInstance getProcessInstance(long processInstanceId, boolean readonly) {
		return processRuntime.getProcessInstance(processInstanceId, readonly);
	}

	public Collection<ProcessInstance> getProcessInstances() {
		return processRuntime.getProcessInstances();
	}

	public void signalEvent(String type, Object event) {
		processRuntime.signalEvent(type, event);
	}

	public void signalEvent(String type, Object event, long processInstanceId) {
		processRuntime.signalEvent(type, event, processInstanceId);
	}

	public ProcessInstance startProcess(String processId) {
		return processRuntime.startProcess(processId);
	}

	public ProcessInstance startProcess(String processId, Map<String, Object> parameters) {
		return processRuntime.startProcess(processId, parameters);
	}

	public ProcessInstance createProcessInstance(String processId, Map<String, Object> parameters) {
		return processRuntime.createProcessInstance(processId, parameters);
	}

	public ProcessInstance startProcessInstance(long processInstanceId) {
		return processRuntime.startProcessInstance(processInstanceId);
	}

	public void addEventListener(ProcessEventListener listener) {
		processRuntime.addEventListener(listener);
	}

	public Collection<ProcessEventListener> getProcessEventListeners() {
		return processRuntime.getProcessEventListeners();
	}

	public void removeEventListener(ProcessEventListener listener) {
		processRuntime.removeEventListener(listener);
	}

	public KnowledgeBase getKieBase() {
		return kbase;
	}

	public WorkItemManager getWorkItemManager() {
        if ( workItemManager == null ) {
            workItemManager = ((SessionConfiguration) sessionConfiguration).getWorkItemManagerFactory().createWorkItemManager(this);
            Map<String, WorkItemHandler> workItemHandlers = ((SessionConfiguration) sessionConfiguration).getWorkItemHandlers();
            if (workItemHandlers != null) {
                for (Map.Entry<String, WorkItemHandler> entry: workItemHandlers.entrySet()) {
                    workItemManager.registerWorkItemHandler(entry.getKey(), entry.getValue());
                }
            }
        }
        return workItemManager;
	}

	public Environment getEnvironment() {
		return environment;
	}
	
	public InternalProcessRuntime getProcessRuntime() {
		return processRuntime;
	}
	
	public KieSessionConfiguration getSessionConfiguration() {
		return sessionConfiguration;
	}

	public TimerService getTimerService() {
		return timerService;
	}

	public void startOperation() {
	}

	public void endOperation() {
	}

	public void executeQueuedActions() {
		if (this.actionQueue.isEmpty()) {
			return;
		}
        try {
            startOperation();
			WorkingMemoryAction action = null;
			while ((action = actionQueue.poll()) != null) {
				try {
					action.execute(this);
				} catch (Exception e) {
					throw new RuntimeException( "Unexpected exception executing action " + action.toString(), e );
				}
			}
        } finally {
            endOperation();
        }
	}

	public Queue<WorkingMemoryAction> getActionQueue() {
		return actionQueue;
	}

	public void queueWorkingMemoryAction(WorkingMemoryAction action) {
		actionQueue.add(action);
	}
	
	public void dispose() {
		if (timerService != null) {
			timerService.shutdown();
		}
	}
	
	public void setId(Long id) {
		this.id = id;
	}

	public int getId() {
		return id.intValue();
	}
	
	public void setEndOperationListener(EndOperationListener listener) {
		
	}

	public int fireAllRules() {
		throw new UnsupportedOperationException();
	}

	public int fireAllRules(int max) {
		throw new UnsupportedOperationException();
	}

	public int fireAllRules(AgendaFilter agendaFilter) {
		throw new UnsupportedOperationException();
	}

	public int fireAllRules(AgendaFilter agendaFilter, int i) {
		throw new UnsupportedOperationException();
	}

	public void fireUntilHalt() {
		throw new UnsupportedOperationException();
	}

	public void fireUntilHalt(AgendaFilter agendaFilter) {
		throw new UnsupportedOperationException();
	}

	public <T> T execute(Command<T> command) {
		throw new UnsupportedOperationException();
	}

	public Calendars getCalendars() {
		throw new UnsupportedOperationException();
	}

	public Map<String, Channel> getChannels() {
		throw new UnsupportedOperationException();
	}

	public Object getGlobal(String identifier) {
		return globals.get(identifier);
	}

	public Globals getGlobals() {
		return globals;
	}

	public SessionClock getSessionClock() {
        return (SessionClock) this.timerService;
	}

	public void registerChannel(String name, Channel channel) {
		throw new UnsupportedOperationException();
	}

	public void setGlobal(String identifier, Object object) {
		throw new UnsupportedOperationException();
	}

	public void unregisterChannel(String name) {
		throw new UnsupportedOperationException();
	}

	public Agenda getAgenda() {
		throw new UnsupportedOperationException();
	}

	public QueryResults getQueryResults(String query, Object... arguments) {
		throw new UnsupportedOperationException();
	}

	public EntryPoint getEntryPoint(String name) {
		throw new UnsupportedOperationException();
	}

	public Collection<? extends EntryPoint> getEntryPoints() {
		throw new UnsupportedOperationException();
	}

	public void halt() {
		throw new UnsupportedOperationException();
	}

	public LiveQuery openLiveQuery(String query, Object[] arguments, ViewChangedEventListener listener) {
		throw new UnsupportedOperationException();
	}

	public String getEntryPointId() {
		throw new UnsupportedOperationException();
	}

	public long getFactCount() {
		throw new UnsupportedOperationException();
	}

	public FactHandle getFactHandle(Object object) {
		throw new UnsupportedOperationException();
	}

	public <T extends FactHandle> Collection<T> getFactHandles() {
		throw new UnsupportedOperationException();
	}

	public <T extends FactHandle> Collection<T> getFactHandles(ObjectFilter filter) {
		throw new UnsupportedOperationException();
	}

	public Object getObject(FactHandle factHandle) {
		throw new UnsupportedOperationException();
	}

	public Collection<Object> getObjects() {
		throw new UnsupportedOperationException();
	}

	public Collection<Object> getObjects(ObjectFilter filter) {
		throw new UnsupportedOperationException();
	}

	public FactHandle insert(Object object) {
		throw new UnsupportedOperationException();
	}

    public void retract(FactHandle handle) {
        throw new UnsupportedOperationException();
    }

    public void delete(FactHandle handle) {
        throw new UnsupportedOperationException();
    }

    public void delete(FactHandle handle, FactHandle.State fhState) {
        throw new UnsupportedOperationException();
    }

	public void update(FactHandle handle, Object object) {
		throw new UnsupportedOperationException();
	}

	public void addEventListener(RuleRuntimeEventListener listener) {
		// Do nothing
	}

	public void addEventListener(AgendaEventListener listener) {
		// Do nothing
	}

	public Collection<AgendaEventListener> getAgendaEventListeners() {
		return new ArrayList<AgendaEventListener>();
	}

	public Collection<RuleRuntimeEventListener> getRuleRuntimeEventListeners() {
		return new ArrayList<RuleRuntimeEventListener>();
	}

	public void removeEventListener(RuleRuntimeEventListener listener) {
		// Do nothing
	}

	public void removeEventListener(AgendaEventListener listener) {
		// Do nothing
	}

	public long getLastIdleTimestamp() {
		throw new UnsupportedOperationException();
	}

    @Override
    public ProcessInstance startProcess(String processId,
            CorrelationKey correlationKey, Map<String, Object> parameters) {

        return processRuntime.startProcess(processId, correlationKey, parameters);
    }

    @Override
    public ProcessInstance createProcessInstance(String processId,
            CorrelationKey correlationKey, Map<String, Object> parameters) {

        return processRuntime.createProcessInstance(processId, correlationKey, parameters);
    }

    @Override
    public ProcessInstance getProcessInstance(CorrelationKey correlationKey) {

        return processRuntime.getProcessInstance(correlationKey);
    }
    
    public void destroy() {
        dispose();
    }

	@Override
	public long getIdentifier() {
		return id;
	}


}
