package p2;

import p1.A;

public class B {
	public String bar= "bar";
	
	public Object m(A a) {
		System.out.println(a.foo);
		System.out.println(a.foo);
		System.out.println(bar);
		return null;
	}
}