/*******************************************************************************
 * Copyright (c) 2005 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package generic;

import java.io.Serializable;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Vector;

public class TestStandardAssignments<T, U extends Number> {
	Object NullType= null;
	
	int i;
	char c;
	boolean b;
	short s;
	long l;
	float f;
	double d;
	byte y;
	
	Integer I;
	Character C;
	Boolean B;
	Short S;
	Long L;
	Float F;
	Double D;
	Byte Y;
	
	Object object;
	String string;
	Vector vector;
	Socket socket;
	Cloneable cloneable;
	Collection collection;
	Serializable serializable;

	Object[] objectArr= null;
	int[] int_arr= null;
	long[] long_arr= null;
	Vector[] vector_arr= null;
	Socket[] socket_arr= null;
	Collection[] collection_arr= null;
	Object[][] objectArrArr= null;
	Collection[][] collection_arrarr= null;
	Vector[][] vector_arrarr= null;
	Socket[][] socket_arrarr= null;
	Cloneable[] cloneable_arr= null;
	Serializable[] serializable_arr= null;
	
	Collection<String> collection_string;
	Collection<Object> collection_object;
	Collection<Number> collection_number;
	Collection<Integer> collection_integer;
	Collection<? extends Number> collection_upper_number;
	Collection<? super Number> collection_lower_number;
	
	Vector<Object> vector_object;
	Vector<Number> vector_number;
	Vector<Integer> vector_integer;
	
	Vector<?> vector_unbound;
	Vector<? extends Number> vector_upper_number;
	Vector<? super Number> vector_lower_number;
	Vector<? extends Exception> vector_upper_exception;
	Vector<? super Exception> vector_lower_exception;

	List<List<? extends String>> list_list_upper_string;
	List<ArrayList<String>> list_arraylist_string;
	
	T t= null;
	U u= null;
	
	Vector<T> vector_t= null;
	Vector<U> vector_u= null;
	Vector<? extends T> vector_upper_t= null;
	Vector<? extends U> vector_upper_u= null;
	Vector<? super T> vector_lower_t= null;
	Vector<? super U> vector_lower_u= null;
	
	Collection<Number>[] coll_string_arr;
	Vector<Number>[] vector_number_arr;
	Vector<Integer>[] vector_integer_arr;
}
