package introInterfaceNoExistingSuperTypeWithSelfUse;

class A {

    public void m(){}

    public void methodUser(A a){
        a.m();
    }

}
