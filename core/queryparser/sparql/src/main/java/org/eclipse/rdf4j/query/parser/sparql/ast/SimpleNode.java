/*******************************************************************************
 * Copyright (c) 2019 Eclipse RDF4J contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 *******************************************************************************/
/* Generated By:JJTree: Do not edit this line. SimpleNode.java Version 7.0 */
/* JavaCCOptions:MULTI=true,NODE_USES_PARSER=false,VISITOR=true,TRACK_TOKENS=false,NODE_PREFIX=AST,NODE_EXTENDS=,NODE_FACTORY=,SUPPORT_CLASS_VISIBILITY_PUBLIC=true */
package org.eclipse.rdf4j.query.parser.sparql.ast;

import java.util.ArrayList;
import java.util.List;

public class SimpleNode implements Node {

	private static final String LINE_SEPARATOR = System.getProperty("line.separator");

	protected Node parent;
	protected Node[] children;
	protected int id;
	protected Object value;
	protected SyntaxTreeBuilder parser;
	private boolean isScopeChange;

	public SimpleNode(int i) {
		id = i;
	}

	public SimpleNode(SyntaxTreeBuilder p, int i) {
		this(i);
		parser = p;
	}

	@Override
	public void jjtOpen() {
	}

	@Override
	public void jjtClose() {
	}

	@Override
	public void jjtSetParent(Node n) {
		parent = n;
	}

	@Override
	public Node jjtGetParent() {
		return parent;
	}

	@Override
	public void jjtAddChild(Node n, int i) {
		if (children == null) {
			children = new Node[i + 1];
		} else if (i >= children.length) {
			Node[] c = new Node[i + 1];
			System.arraycopy(children, 0, c, 0, children.length);
			children = c;
		}
		children[i] = n;
	}

	@Override
	public Node jjtGetChild(int i) {
		return children[i];
	}

	@Override
	public int jjtGetNumChildren() {
		return (children == null) ? 0 : children.length;
	}

	public void jjtSetValue(Object value) {
		this.value = value;
	}

	public Object jjtGetValue() {
		return value;
	}

	/**
	 * Accept the visitor.
	 **/
	@Override
	public Object jjtAccept(SyntaxTreeBuilderVisitor visitor, Object data)
			throws VisitorException {
		return visitor.visit(this, data);
	}

	/**
	 * Accept the visitor.
	 **/
	public Object childrenAccept(SyntaxTreeBuilderVisitor visitor, Object data) throws VisitorException {
		if (children != null) {
			for (Node childNode : children) {
				// Note: modified JavaCC code, child's data no longer ignored
				data = childNode.jjtAccept(visitor, data);
			}
		}

		return data;
	}

	/*
	 * You can override these two methods in subclasses of SimpleNode to customize the way the node appears when the
	 * tree is dumped. If your output uses more than one line you should override toString(String), otherwise overriding
	 * toString() is probably all you need to do.
	 */

	public String toString() {
		return SyntaxTreeBuilderTreeConstants.jjtNodeName[id];
	}

	public String toString(String prefix) {
		return prefix + this;
	}

	/*
	 * Override this method if you want to customize how the node dumps out its children.
	 */

	public void dump(String prefix) {
		System.out.println(toString(prefix));
		if (children != null) {
			for (int i = 0; i < children.length; ++i) {
				SimpleNode n = (SimpleNode) children[i];
				if (n != null) {
					n.dump(prefix + " ");
				}
			}
		}
	}

	@Override
	public int getId() {
		return id;
	}

	/**
	 * Gets the (first) child of this node that is of the specific type.
	 *
	 * @param type The type of the child node that should be returned.
	 * @return The (first) child node of the specified type, or <var>null</var> if no such child node was found.
	 */
	public <T extends Node> T jjtGetChild(Class<T> type) {
		if (children != null) {
			for (Node n : children) {
				if (type.isInstance(n)) {
					return (T) n;
				}
			}
		}

		return null;
	}

	public <T extends Node> List<T> jjtGetChildren(Class<T> type) {
		if (children == null) {
			return List.of();
		}

		List<T> result = new ArrayList<>(children.length);

		for (Node n : children) {
			if (type.isInstance(n)) {
				result.add((T) n);
			}
		}

		return result;
	}

	public Node[] jjtGetChildren() {
		return children;
	}

	public void jjtReplaceWith(Node newNode) {
		if (parent != null) {
			parent.jjtReplaceChild(this, newNode);
		}

		if (children != null) {
			for (Node childNode : children) {
				childNode.jjtSetParent(newNode);
			}
		}
	}

	public void jjtReplaceChild(Node oldNode, Node newNode) {
		if (children != null) {
			for (int i = 0; i < children.length; i++) {
				if (children[i] == oldNode) {
					children[i] = newNode;
				}
			}
		}
	}

	@Override
	public void jjtAppendChild(Node n) {
		jjtAddChild(n, children == null ? 0 : children.length);
	}

	/**
	 * Check if this AST node constitutes a variable scope change.
	 *
	 * @return the isScopeChange
	 */
	public boolean isScopeChange() {
		return isScopeChange;
	}

	/**
	 * @param isScopeChange the isScopeChange to set
	 */
	public void setScopeChange(boolean isScopeChange) {
		this.isScopeChange = isScopeChange;
	}

}

/* JavaCC - OriginalChecksum=43de2f398dac53c0bbcf106e4a3ca6de (do not edit this line) */