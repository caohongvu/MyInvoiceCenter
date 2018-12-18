package net.cis.service.impl;

import java.util.List;

import javax.annotation.PostConstruct;
import org.modelmapper.ModelMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import net.cis.common.util.constant.BkavConfigurationConstant;
import net.cis.common.util.constant.InvoiceConstant;
import net.cis.dto.BkavTicketDto;
import net.cis.jpa.entity.EInvoiceEntity;
import net.cis.repository.invoice.center.EInvoiceRepository;
import net.cis.service.EInvoiceService;
import net.cis.service.EmailService;
import net.cis.service.InvoiceService;
/**
 * Created by NhanNguyen on 19/10/2018
 */
@Service
public class EInvoiceServiceImpl implements EInvoiceService {

	ModelMapper mapper;
	
	@Autowired
	InvoiceService invoiceService;
	
	@Autowired
	EInvoiceRepository eInvoiceRepository;
	
	@Autowired
	EmailService emailService;
	
	
	@PostConstruct
	public void initialize() throws Exception {
		mapper = new ModelMapper();
	}

	@Scheduled(fixedDelay=5*60*1000) //5 phút reload lại danh sách 1 lần
	private void checkAndResendNotMatchInvoice() throws Exception {
		List<EInvoiceEntity> notMatchStatusEInvoices = eInvoiceRepository.findByInvoiceStatus(BkavConfigurationConstant.INVOICE_STATUS_FAILED);
		
		for(EInvoiceEntity invoiceEntity : notMatchStatusEInvoices) {
			//call qua BKAV để check status của invoice
			int status = invoiceService.getInvoiceStatus(invoiceEntity);
			if (status == 0) {
				//Nếu đã bên BKAV đã phát hành invoice này, thì chỉ việc update lại status của invoice này bên mình
				updateEInvoiceStatus(invoiceEntity.getId(), BkavConfigurationConstant.INVOICE_STATUS_SUCCESS);
			} else {
				// Nếu bên BKAV chưa phát hành thành công invoice này, 
				// thì gọi phát hành lại và update lại status của invoice này theo kết quả trả về
				boolean isReCreatedSuccessfully = invoiceService.reCreateInvoice(invoiceEntity);
				
				//Nếu đã gửi qua bên BKAV và nhận về kết quả không thành công, 
				// thì update lại status của invoice thành không thành công, và gửi email thông báo
				isReCreatedSuccessfully = false;
				if (isReCreatedSuccessfully == false) {
					updateEInvoiceStatus(invoiceEntity.getId(), BkavConfigurationConstant.INVOICE_STATUS_RECREATED_FAILED);
				
					//Kiểm tra chỉ send email trong trường hợp không thể xuất hóa đơn thành công nhé, 
					// edit lại content để lấy đúng thông tin cần thiết
					StringBuilder emailContent = new StringBuilder();

					emailContent.append("Không thể phát hành hóa đơn điện tử cho hóa đơn:<strong>"+invoiceEntity.getInvoiceCode() +"</strong>");
					emailContent.append("Của khách hàng:<strong>"+invoiceEntity.getCustomerEmail() + " - " +invoiceEntity.getCustomerPhone() +"</strong>");
					emailContent.append("Ở điểm đỗ:<strong>" + invoiceEntity.getCppId() + "</strong>");
					
					emailService.send(InvoiceConstant.EMAIL_FAILURE_TITLE, emailContent.toString());
				}
			}
		}
	}
	
	@Override
	public long createEInvoice(BkavTicketDto bkavTicketDto) {
		EInvoiceEntity eInvoice = parseEInvoice(bkavTicketDto);
		try {
			eInvoiceRepository.save(eInvoice);
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		
		return eInvoice.getId();
	}
	
	public static EInvoiceEntity parseEInvoice(BkavTicketDto bkavTicketDto) {
		EInvoiceEntity eInvoice = new EInvoiceEntity();
		
		eInvoice.setCustomerEmail(bkavTicketDto.getReceiverEmail());
		eInvoice.setCustomerPhone(bkavTicketDto.getReceiverMobile());
		eInvoice.setInvoiceGUID("");
		eInvoice.setInvoiceStatus(0);
		eInvoice.setProviderId(bkavTicketDto.getProviderId());
		eInvoice.setTransactionAmount(bkavTicketDto.getTransactionAmount());
		eInvoice.setTicketId(bkavTicketDto.getTicketId());
		eInvoice.setCppId(bkavTicketDto.getCppId());
		eInvoice.setTransactionId(bkavTicketDto.getTransactionId());
		eInvoice.setRequestBody("");
		eInvoice.setResponseBody("");
		eInvoice.setPartnerInvoiceStringId(bkavTicketDto.getPartnerInvoiceStringId());
		
		return eInvoice;
	}

	@Override
	public void updateEInvoice(long id, int invoiceStatus, String invoiceGUID, String invoiceCode, String requestBody, String responseBody) {
		EInvoiceEntity eInvoice = eInvoiceRepository.findById(id);
		eInvoice.setInvoiceStatus(invoiceStatus);
		eInvoice.setInvoiceGUID(invoiceGUID);
		eInvoice.setInvoiceCode(invoiceCode);
		eInvoice.setRequestBody(requestBody);
		eInvoice.setResponseBody(responseBody);
		
		eInvoiceRepository.save(eInvoice);
	}

	@Override
	public List<EInvoiceEntity> getByTicketId(String ticketId) {
		return eInvoiceRepository.findByTicketId(ticketId);
	}

	@Override
	public EInvoiceEntity getByInvoiceGUID(String invoiceGUID) {
		return eInvoiceRepository.findByInvoiceGUID(invoiceGUID);
	}

	@Override
	public void updateEInvoiceStatus(long id, int invoiceStatus) {
		EInvoiceEntity eInvoice = eInvoiceRepository.findById(id);
		eInvoice.setInvoiceStatus(invoiceStatus);
		
		eInvoiceRepository.save(eInvoice);	
	}

	@Override
	public List<EInvoiceEntity> getInvoiceFailed() {
		List<EInvoiceEntity> eInvoices = eInvoiceRepository.findByInvoiceStatus(BkavConfigurationConstant.INVOICE_STATUS_FAILED);
		return eInvoices;
	}

}
