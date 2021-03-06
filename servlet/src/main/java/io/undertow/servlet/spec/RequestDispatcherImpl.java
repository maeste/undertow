/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.undertow.servlet.spec;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import io.undertow.server.handlers.blocking.BlockingHttpServerExchange;
import io.undertow.servlet.handlers.ServletInitialHandler;
import io.undertow.servlet.handlers.ServletPathMatch;

/**
 * @author Stuart Douglas
 */
public class RequestDispatcherImpl implements RequestDispatcher {

    private final String path;
    private final ServletContextImpl servletContext;
    private final ServletInitialHandler handler;
    private final ServletPathMatch pathMatch;
    private final boolean named;

    public RequestDispatcherImpl(final String path, final ServletContextImpl servletContext) {
        this.path = path;
        this.servletContext = servletContext;
        this.pathMatch = servletContext.getDeployment().getServletPaths().getServletHandlerByPath(path);
        this.handler = pathMatch.getHandler();
        this.named = false;
    }


    public RequestDispatcherImpl(final ServletInitialHandler handler, final ServletContextImpl servletContext) {
        this.handler = handler;
        this.named = true;
        this.servletContext = servletContext;
        this.path = null;
        this.pathMatch = null;
    }

    @Override
    public void forward(final ServletRequest request, final ServletResponse response) throws ServletException, IOException {
        HttpServletRequestImpl requestImpl = HttpServletRequestImpl.getRequestImpl(request);
        final HttpServletResponseImpl responseImpl = HttpServletResponseImpl.getResponseImpl(response);
        final BlockingHttpServerExchange exchange = requestImpl.getExchange();
        response.resetBuffer();


        final ServletRequest oldRequest = exchange.getExchange().getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
        final ServletResponse oldResponse = exchange.getExchange().getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);
        exchange.getExchange().putAttachment(HttpServletRequestImpl.DISPATCHER_TYPE_ATTACHMENT_KEY, DispatcherType.FORWARD);

        Map<String, Deque<String>> queryParameters = requestImpl.getQueryParameters();

        if (!named) {

            //only update if this is the first forward
            if (request.getAttribute(FORWARD_REQUEST_URI) == null) {
                request.setAttribute(FORWARD_REQUEST_URI, requestImpl.getRequestURI());
                request.setAttribute(FORWARD_CONTEXT_PATH, requestImpl.getContextPath());
                request.setAttribute(FORWARD_SERVLET_PATH, requestImpl.getServletPath());
                request.setAttribute(FORWARD_PATH_INFO, requestImpl.getPathInfo());
                request.setAttribute(FORWARD_QUERY_STRING, requestImpl.getQueryString());
            }

            String newQueryString = "";
            int qsPos = path.indexOf("?");
            String newServletPath = path;
            if (qsPos != -1) {
                newQueryString = newServletPath.substring(qsPos + 1);
                newServletPath = newServletPath.substring(0, qsPos);
            }
            String newRequestUri = servletContext.getContextPath() + newServletPath;

            //todo: a more efficent impl
            Map<String, Deque<String>> newQueryParameters = new HashMap<String, Deque<String>>();
            for (String part : newQueryString.split("&")) {
                String name = part;
                String value = "";
                int equals = part.indexOf('=');
                if (equals != -1) {
                    name = part.substring(0, equals);
                    value = part.substring(equals + 1);
                }
                Deque<String> queue = newQueryParameters.get(name);
                if (queue == null) {
                    newQueryParameters.put(name, queue = new ArrayDeque<String>(1));
                }
                queue.add(value);
            }
            requestImpl.setQueryParameters(newQueryParameters);

            requestImpl.getExchange().getExchange().setRelativePath(newServletPath);
            requestImpl.getExchange().getExchange().setQueryString(newQueryString);
            requestImpl.getExchange().getExchange().setRequestPath(newRequestUri);
            requestImpl.getExchange().getExchange().setRequestURI(newRequestUri);
            requestImpl.getExchange().getExchange().putAttachment(ServletPathMatch.ATTACHMENT_KEY, pathMatch);
            requestImpl.setServletContext(servletContext);
            responseImpl.setServletContext(servletContext);
        }

        try {
            try {
                exchange.getExchange().putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, request);
                exchange.getExchange().putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, response);
                handler.handleRequest(exchange);
            } catch (ServletException e) {
                throw e;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } finally {
            exchange.getExchange().putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, oldRequest);
            exchange.getExchange().putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, oldResponse);
        }
    }

    @Override
    public void include(final ServletRequest request, final ServletResponse response) throws ServletException, IOException {

        HttpServletRequestImpl requestImpl = HttpServletRequestImpl.getRequestImpl(request);
        final HttpServletResponseImpl responseImpl = HttpServletResponseImpl.getResponseImpl(response);
        final BlockingHttpServerExchange exchange = requestImpl.getExchange();

        final ServletRequest oldRequest = exchange.getExchange().getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
        final ServletResponse oldResponse = exchange.getExchange().getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);
        exchange.getExchange().putAttachment(HttpServletRequestImpl.DISPATCHER_TYPE_ATTACHMENT_KEY, DispatcherType.INCLUDE);

        Object requestUri = null;
        Object contextPath = null;
        Object servletPath = null;
        Object pathInfo = null;
        Object queryString = null;
        Map<String, Deque<String>> queryParameters = requestImpl.getQueryParameters();

        if (!named) {
            requestUri = request.getAttribute(INCLUDE_REQUEST_URI);
            contextPath = request.getAttribute(INCLUDE_CONTEXT_PATH);
            servletPath = request.getAttribute(INCLUDE_SERVLET_PATH);
            pathInfo = request.getAttribute(INCLUDE_PATH_INFO);
            queryString = request.getAttribute(INCLUDE_QUERY_STRING);

            String newQueryString = "";
            int qsPos = path.indexOf("?");
            String newServletPath = path;
            if (qsPos != -1) {
                newQueryString = newServletPath.substring(qsPos + 1);
                newServletPath = newServletPath.substring(0, qsPos);
            }
            String newRequestUri = servletContext.getContextPath() + newServletPath;

            //todo: a more efficent impl
            Map<String, Deque<String>> newQueryParameters = new HashMap<String, Deque<String>>();
            for (String part : newQueryString.split("&")) {
                String name = part;
                String value = "";
                int equals = part.indexOf('=');
                if (equals != -1) {
                    name = part.substring(0, equals);
                    value = part.substring(equals + 1);
                }
                Deque<String> queue = newQueryParameters.get(name);
                if (queue == null) {
                    newQueryParameters.put(name, queue = new ArrayDeque<String>(1));
                }
                queue.add(value);
            }
            requestImpl.setQueryParameters(newQueryParameters);

            request.setAttribute(INCLUDE_REQUEST_URI, newRequestUri);
            request.setAttribute(INCLUDE_CONTEXT_PATH, servletContext.getContextPath());
            request.setAttribute(INCLUDE_SERVLET_PATH, newServletPath);
            request.setAttribute(INCLUDE_PATH_INFO, pathMatch.getRemaining());
            request.setAttribute(INCLUDE_QUERY_STRING, newQueryString);
        }
        boolean inInclude = responseImpl.isInsideInclude();
        responseImpl.setInsideInclude(true);

        ServletContextImpl oldContext = requestImpl.getServletContext();
        try {
            requestImpl.setServletContext(servletContext);
            responseImpl.setServletContext(servletContext);
            try {
                exchange.getExchange().putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, request);
                exchange.getExchange().putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, response);
                handler.handleRequest(exchange);
            } catch (ServletException e) {
                throw e;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } finally {
            responseImpl.setInsideInclude(inInclude);
            requestImpl.setServletContext(oldContext);
            responseImpl.setServletContext(oldContext);
            exchange.getExchange().putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, oldRequest);
            exchange.getExchange().putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, oldResponse);
            if (!named) {
                request.setAttribute(INCLUDE_REQUEST_URI, requestUri);
                request.setAttribute(INCLUDE_CONTEXT_PATH, contextPath);
                request.setAttribute(INCLUDE_SERVLET_PATH, servletPath);
                request.setAttribute(INCLUDE_PATH_INFO, pathInfo);
                request.setAttribute(INCLUDE_QUERY_STRING, queryString);
                requestImpl.setQueryParameters(queryParameters);
            }
        }
    }

    public void error(final ServletRequest request, final ServletResponse response, final String servletName, final String message) throws ServletException, IOException {
        error(request, response, servletName, null, message);
    }

    public void error(final ServletRequest request, final ServletResponse response, final String servletName) throws ServletException, IOException {
        error(request, response, servletName, null, null);
    }

    public void error(final ServletRequest request, final ServletResponse response, final String servletName, final Throwable exception) throws ServletException, IOException {
        error(request, response, servletName, exception, exception.getMessage());
    }


    private void error(final ServletRequest request, final ServletResponse response, final String servletName, final Throwable exception, final String message) throws ServletException, IOException {
        HttpServletRequestImpl requestImpl = HttpServletRequestImpl.getRequestImpl(request);
        final HttpServletResponseImpl responseImpl = HttpServletResponseImpl.getResponseImpl(response);
        final BlockingHttpServerExchange exchange = requestImpl.getExchange();
        response.resetBuffer();


        final ServletRequest oldRequest = exchange.getExchange().getAttachment(HttpServletRequestImpl.ATTACHMENT_KEY);
        final ServletResponse oldResponse = exchange.getExchange().getAttachment(HttpServletResponseImpl.ATTACHMENT_KEY);
        exchange.getExchange().putAttachment(HttpServletRequestImpl.DISPATCHER_TYPE_ATTACHMENT_KEY, DispatcherType.ERROR);


        //only update if this is the first forward
        request.setAttribute(ERROR_REQUEST_URI, requestImpl.getRequestURI());
        request.setAttribute(ERROR_SERVLET_NAME, servletName);
        if (exception != null) {
            request.setAttribute(ERROR_EXCEPTION, exception);
            request.setAttribute(ERROR_EXCEPTION_TYPE, exception.getClass());
        }
        request.setAttribute(ERROR_MESSAGE, message);
        request.setAttribute(ERROR_STATUS_CODE, exchange.getExchange().getResponseCode());

        String newQueryString = "";
        int qsPos = path.indexOf("?");
        String newServletPath = path;
        if (qsPos != -1) {
            newQueryString = newServletPath.substring(qsPos + 1);
            newServletPath = newServletPath.substring(0, qsPos);
        }
        String newRequestUri = servletContext.getContextPath() + newServletPath;

        //todo: a more efficent impl
        Map<String, Deque<String>> newQueryParameters = new HashMap<String, Deque<String>>();
        for (String part : newQueryString.split("&")) {
            String name = part;
            String value = "";
            int equals = part.indexOf('=');
            if (equals != -1) {
                name = part.substring(0, equals);
                value = part.substring(equals + 1);
            }
            Deque<String> queue = newQueryParameters.get(name);
            if (queue == null) {
                newQueryParameters.put(name, queue = new ArrayDeque<String>(1));
            }
            queue.add(value);
        }
        requestImpl.setQueryParameters(newQueryParameters);

        requestImpl.getExchange().getExchange().setRelativePath(newServletPath);
        requestImpl.getExchange().getExchange().setQueryString(newQueryString);
        requestImpl.getExchange().getExchange().setRequestPath(newRequestUri);
        requestImpl.getExchange().getExchange().setRequestURI(newRequestUri);
        requestImpl.getExchange().getExchange().putAttachment(ServletPathMatch.ATTACHMENT_KEY, pathMatch);
        requestImpl.setServletContext(servletContext);
        responseImpl.setServletContext(servletContext);


        try {
            try {
                exchange.getExchange().putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, request);
                exchange.getExchange().putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, response);
                handler.handleRequest(exchange);
            } catch (ServletException e) {
                throw e;
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } finally {
            exchange.getExchange().putAttachment(HttpServletRequestImpl.ATTACHMENT_KEY, oldRequest);
            exchange.getExchange().putAttachment(HttpServletResponseImpl.ATTACHMENT_KEY, oldResponse);
        }
    }
}
