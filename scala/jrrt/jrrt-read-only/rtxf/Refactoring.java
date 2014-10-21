//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vJAXB 2.1.10 in JDK 6 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2010.12.16 at 08:11:09 AM GMT 
//


package rtxf;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for anonymous complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType>
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;choice>
 *         &lt;element ref="{}extract_block"/>
 *         &lt;element ref="{}extract_class"/>
 *         &lt;element ref="{}extract_constant"/>
 *         &lt;element ref="{}rename"/>
 *       &lt;/choice>
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "", propOrder = {
    "extractBlock",
    "extractClass",
    "extractConstant",
    "rename"
})
@XmlRootElement(name = "refactoring")
public class Refactoring {

    @XmlElement(name = "extract_block")
    protected ExtractBlock extractBlock;
    @XmlElement(name = "extract_class")
    protected ExtractClass extractClass;
    @XmlElement(name = "extract_constant")
    protected ExtractConstant extractConstant;
    protected Rename rename;

    /**
     * Gets the value of the extractBlock property.
     * 
     * @return
     *     possible object is
     *     {@link ExtractBlock }
     *     
     */
    public ExtractBlock getExtractBlock() {
        return extractBlock;
    }

    /**
     * Sets the value of the extractBlock property.
     * 
     * @param value
     *     allowed object is
     *     {@link ExtractBlock }
     *     
     */
    public void setExtractBlock(ExtractBlock value) {
        this.extractBlock = value;
    }

    /**
     * Gets the value of the extractClass property.
     * 
     * @return
     *     possible object is
     *     {@link ExtractClass }
     *     
     */
    public ExtractClass getExtractClass() {
        return extractClass;
    }

    /**
     * Sets the value of the extractClass property.
     * 
     * @param value
     *     allowed object is
     *     {@link ExtractClass }
     *     
     */
    public void setExtractClass(ExtractClass value) {
        this.extractClass = value;
    }

    /**
     * Gets the value of the extractConstant property.
     * 
     * @return
     *     possible object is
     *     {@link ExtractConstant }
     *     
     */
    public ExtractConstant getExtractConstant() {
        return extractConstant;
    }

    /**
     * Sets the value of the extractConstant property.
     * 
     * @param value
     *     allowed object is
     *     {@link ExtractConstant }
     *     
     */
    public void setExtractConstant(ExtractConstant value) {
        this.extractConstant = value;
    }

    /**
     * Gets the value of the rename property.
     * 
     * @return
     *     possible object is
     *     {@link Rename }
     *     
     */
    public Rename getRename() {
        return rename;
    }

    /**
     * Sets the value of the rename property.
     * 
     * @param value
     *     allowed object is
     *     {@link Rename }
     *     
     */
    public void setRename(Rename value) {
        this.rename = value;
    }

}
