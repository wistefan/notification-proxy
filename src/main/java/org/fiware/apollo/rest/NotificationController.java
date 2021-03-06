package org.fiware.apollo.rest;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Controller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.fiware.ngsi.model.EntityVO;
import org.fiware.apollo.api.NotificationApi;
import org.fiware.apollo.exception.CreationFailureException;
import org.fiware.apollo.exception.NoSuchEntityException;
import org.fiware.apollo.exception.UpdateFailureException;
import org.fiware.apollo.mapping.EntityMapper;
import org.fiware.apollo.model.NotificationVO;
import org.fiware.apollo.repository.EntityRepository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementation of Apollo's NGSI-LD compatible notification api
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class NotificationController implements NotificationApi {

	private final EntityMapper entityMapper;
	private final EntityRepository entityRepository;

	@Override
	public HttpResponse<Object> receiveNotification(NotificationVO notificationVO) {

		List<Boolean> resultList = notificationVO.getData().stream().map(entityMapper::fixedNotifiedEntityVOToEntityVO).map(this::updateEntityInBroker).collect(Collectors.toList());
		if (resultList.contains(true) && !resultList.contains(false)) {
			// everything succeeded
			return HttpResponse.noContent();
		} else if (resultList.contains(true) && resultList.contains(false)) {
			// some failed, some succeeded
			return HttpResponse.status(HttpStatus.MULTI_STATUS);
		} else {
			// everything failed
			return HttpResponse.badRequest();
		}
	}

	// helper method to handle create or update, depending on the response from the context-broker
	// - update in case it already exists
	// - create if no such entity is found
	private boolean updateEntityInBroker(EntityVO entityVO) {
		try {
			entityRepository.updateEntity(entityVO);
		} catch (NoSuchEntityException e) {
			try {
				entityRepository.createEntity(entityVO);
			} catch (CreationFailureException ex) {
				log.warn("Was not able to create entity {}.", entityVO.id(), ex);
				return false;
			}
		} catch (UpdateFailureException e) {
			log.warn("Was not able to update entity {}.", entityVO.id(), e);
			return false;
		}
		return true;
	}
}
